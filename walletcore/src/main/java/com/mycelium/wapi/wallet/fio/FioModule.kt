package com.mycelium.wapi.wallet.fio

import com.mrd.bitlib.crypto.HdKeyNode
import com.mrd.bitlib.crypto.InMemoryPrivateKey
import com.mrd.bitlib.model.NetworkParameters
import com.mrd.bitlib.model.hdpath.HdKeyPath
import com.mycelium.generated.wallet.database.WalletDB
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.coins.Balance
import com.mycelium.wapi.wallet.fio.coins.FIOMain
import com.mycelium.wapi.wallet.fio.coins.FIOTest
import com.mycelium.wapi.wallet.genericdb.Backing
import com.mycelium.wapi.wallet.manager.Config
import com.mycelium.wapi.wallet.manager.WalletModule
import com.mycelium.wapi.wallet.metadata.IMetaDataStorage
import fiofoundation.io.fiosdk.FIOSDK
import fiofoundation.io.fiosdk.interfaces.ISerializationProvider
import java.lang.IllegalStateException
import java.text.DateFormat
import java.util.*

class FioModule(
        private val serializationProvider : ISerializationProvider,
        private val secureStore: SecureKeyValueStore,
        private val backing: Backing<FioAccountContext>,
        private val walletDB: WalletDB,
        private val networkParameters: NetworkParameters,
        metaDataStorage: IMetaDataStorage,
        private val fioKeyManager: FioKeyManager,
        private val accountListener: AccountListener?,
        private val walletManager: WalletManager
) : WalletModule(metaDataStorage) {

    private val coinType = if (networkParameters.isProdnet) FIOMain else FIOTest
    private val accounts = mutableMapOf<UUID, FioAccount>()
    override val id = ID

    init {
        assetsList.add(coinType)
    }

    fun getAllRegisteredFioNames() = accounts.values.map { it.registeredFIONames }.flatten()

    fun getAllRegisteredFioDomains() = accounts.values.map { it.registeredFIODomains }.flatten()

    fun getFioAccountByFioName(fioName: String): UUID? = accounts.values.firstOrNull { fioAccount ->
        fioName in fioAccount.registeredFIONames.map { it.name }
    }?.id

    fun getFioAccountByFioDomain(fioDomain: String): UUID? = accounts.values.firstOrNull { fioAccount ->
        fioDomain in fioAccount.registeredFIODomains.map { it.domain }
    }?.id

    fun getFIONames(domainName: String): List<RegisteredFIOName> {
        return getAllRegisteredFioNames().filter { it.name.split("@")[1] == domainName }
    }

    fun getFIONames(account: WalletAccount<*>): List<RegisteredFIOName> {
        if (account is FioAccount) {
            return account.registeredFIONames
        }
        
        val fioNames = walletDB.fioNameAccountMappingsQueries.selectFioNamesByAccountUuid(account.id).executeAsList()
        return getAllRegisteredFioNames().filter { fioNames.contains(it.name) }
    }

    fun getKnownNames(): List<FioName> = walletDB.fioKnownNamesQueries.selectAllFioKnownNames()
            .executeAsList().sortedBy { "${it.name}@${it.domain}" }

    fun addKnownName(fioName: FioName) = walletDB.fioKnownNamesQueries.insert(fioName)

    fun deleteKnownName(fioName: FioName) = walletDB.fioKnownNamesQueries.delete(fioName)

    fun getConnectedAccounts(fioName: String): List<WalletAccount<*>> {
        var connected = ArrayList<WalletAccount<*>>()
        var accountsList = walletDB.fioNameAccountMappingsQueries.selectAccountsUuidByFioName(fioName).executeAsList()
        accountsList.forEach {
            var account = walletManager.getAccount(it)
            if (account != null) {
                connected.add(account)
            }
        }
        return connected
    }

    fun mapFioNameToAccounts(fioName: String, accounts: List<WalletAccount<*>>) {
        walletDB.fioNameAccountMappingsQueries.deleteAllMappings(fioName);
        accounts.forEach {
            walletDB.fioNameAccountMappingsQueries.insertMapping(fioName, it.receiveAddress.toString(), it.basedOnCoinType.symbol, it.basedOnCoinType.symbol, it.id)
        }
    }

    private fun getFioSdk(accountIndex: Int): FIOSDK {
        val privkeyString = fioKeyManager.getFioPrivateKey(accountIndex).getBase58EncodedPrivateKey(networkParameters)
        return FIOSDK.getInstance(privkeyString, FIOSDK.derivedPublicKey(privkeyString), serializationProvider, coinType.url)
    }

    private fun getFioSdkByNode(node: HdKeyNode): FIOSDK {
        val privkeyString = node.createChildNode(0).createChildNode(0).privateKey.getBase58EncodedPrivateKey(networkParameters)
        return FIOSDK.getInstance(privkeyString, FIOSDK.derivedPublicKey(privkeyString), serializationProvider, coinType.url)
    }

    private fun getFioSdkByPrivkey(privateKey: InMemoryPrivateKey): FIOSDK {
        val privkeyString = privateKey.getBase58EncodedPrivateKey(networkParameters)
        return FIOSDK.getInstance(privkeyString, FIOSDK.derivedPublicKey(privkeyString), serializationProvider, coinType.url)
    }

    private fun getFioAddressByPrivkey(privateKey: InMemoryPrivateKey): FioAddress {
        val privkeyString = privateKey.getBase58EncodedPrivateKey(networkParameters)
        return FioAddress(coinType, FioAddressData(FIOSDK.derivedPublicKey(privkeyString)))
    }

    override fun loadAccounts(): Map<UUID, WalletAccount<*>> =
            backing.loadAccountContexts()
                    .associateBy({ it.uuid }, { accountFromUUID(it.uuid) })

    private fun accountFromUUID(uuid: UUID): WalletAccount<*> {
        return if (secureStore.getPlaintextValue(uuid.toString().toByteArray()) != null) {
            val fioAddress = FioAddress(coinType, FioAddressData(String(secureStore.getPlaintextValue(uuid.toString().toByteArray()))))
            val accountContext = createAccountContext(uuid)
            val fioAccountBacking = FioAccountBacking(walletDB, accountContext.uuid, coinType)
            val account = FioAccount(accountContext = accountContext, backing = fioAccountBacking,
                    accountListener = accountListener, address = fioAddress)
            accounts[account.id] = account
            account
        } else {
            val accountContext = createAccountContext(uuid)
            val fioAccountBacking = FioAccountBacking(walletDB, accountContext.uuid, coinType)
            val account = FioAccount(accountContext, fioAccountBacking, accountListener,
                    getFioSdk(accountContext.accountIndex))
            accounts[account.id] = account
            account
        }
    }

    override fun createAccount(config: Config): WalletAccount<*> {
        val result: WalletAccount<*>
        val baseLabel: String
        when (config) {
            is FIOMasterseedConfig -> {
                val newIndex = getCurrentBip44Index() + 1
                val accountContext = createAccountContext(fioKeyManager.getUUID(newIndex))
                baseLabel = accountContext.accountName
                backing.createAccountContext(accountContext)
                val fioAccountBacking = FioAccountBacking(walletDB, accountContext.uuid, coinType)
                result = FioAccount(accountContext, fioAccountBacking, accountListener, getFioSdk(newIndex))
            }
            is FIOUnrelatedHDConfig -> {
                val hdKeyNode = config.hdKeyNodes[0]
                val uuid = hdKeyNode.uuid
                val accountContext = createAccountContext(uuid, isReadOnly = true)
                baseLabel = accountContext.accountName
                backing.createAccountContext(accountContext)
                val fioAccountBacking = FioAccountBacking(walletDB, accountContext.uuid, coinType)
                result = FioAccount(accountContext, fioAccountBacking, accountListener, getFioSdkByNode(hdKeyNode))
            }
            is FIOAddressConfig -> {
                val pubkeyString = when (config.address.getSubType()) {
                    FioAddressSubtype.ACTOR.toString() -> FioTransactionHistoryService.getPubkeyByActor(config.address.toString(), coinType)
                    FioAddressSubtype.ADDRESS.toString() -> FioTransactionHistoryService.getPubkeyByFioAddress(config.address.toString(), coinType)
                    else -> config.address.toString()
                }
                val fioAddress = FioAddress(coinType, FioAddressData(pubkeyString
                        ?: throw IllegalStateException("Cannot find public key for: ${config.address}")))
                val uuid = UUID.nameUUIDFromBytes(fioAddress.getBytes())
                secureStore.storePlaintextValue(uuid.toString().toByteArray(),
                        fioAddress.toString().toByteArray())
                val accountContext = createAccountContext(uuid, isReadOnly = true)
                baseLabel = accountContext.accountName
                backing.createAccountContext(accountContext)
                val fioAccountBacking = FioAccountBacking(walletDB, accountContext.uuid, coinType)
                result = FioAccount(accountContext = accountContext, backing = fioAccountBacking,
                        accountListener = accountListener, address = fioAddress)
            }
            is FIOPrivateKeyConfig -> {
                val uuid = UUID.nameUUIDFromBytes(getFioAddressByPrivkey(config.privkey).getBytes())
                secureStore.encryptAndStoreValue(uuid.toString().toByteArray(),
                        config.privkey.toString().toByteArray(), AesKeyCipher.defaultKeyCipher())
                val accountContext = createAccountContext(uuid, isReadOnly = true)
                baseLabel = accountContext.accountName
                backing.createAccountContext(accountContext)
                val fioAccountBacking = FioAccountBacking(walletDB, accountContext.uuid, coinType)
                result = FioAccount(accountContext, fioAccountBacking, accountListener, getFioSdkByPrivkey(config.privkey))
            }
            else -> {
                throw NotImplementedError("Unknown config")
            }
        }
        accounts[result.id] = result
        result.label = createLabel(baseLabel)
        storeLabel(result.id, result.label)
        return result
    }

    private fun getCurrentBip44Index() = accounts.values
            .filter { it.isDerivedFromInternalMasterseed }
            .maxBy { it.accountIndex }
            ?.accountIndex
            ?: -1

    override fun canCreateAccount(config: Config): Boolean {
        return config is FIOMasterseedConfig || config is FIOAddressConfig || config is FIOPrivateKeyConfig
                || config is FIOUnrelatedHDConfig
    }

    override fun deleteAccount(walletAccount: WalletAccount<*>, keyCipher: KeyCipher): Boolean {
        return if (walletAccount is FioAccount) {
            if (secureStore.getPlaintextValue(walletAccount.id.toString().toByteArray()) != null) {
                secureStore.deletePlaintextValue(walletAccount.id.toString().toByteArray())
            }
            backing.deleteAccountContext(walletAccount.id)
            accounts.remove(walletAccount.id)
            true
        } else {
            false
        }
    }

    override fun getAccounts(): List<WalletAccount<*>> {
        return accounts.values.toList()
    }

    override fun getAccountById(id: UUID): WalletAccount<*>? {
        return accounts[id]
    }

    private fun createAccountContext(uuid: UUID, isReadOnly: Boolean = false): FioAccountContext {
        val accountContextInDB = backing.loadAccountContext(uuid)
        return if (accountContextInDB != null) {
            FioAccountContext(accountContextInDB.uuid,
                    accountContextInDB.currency,
                    accountContextInDB.accountName,
                    accountContextInDB.balance,
                    backing::updateAccountContext,
                    accountContextInDB.accountIndex,
                    accountContextInDB.registeredFIONames,
                    accountContextInDB.registeredFIODomains,
                    accountContextInDB.archived,
                    accountContextInDB.blockHeight,
                    accountContextInDB.actionSequenceNumber)
        } else {
            FioAccountContext(
                    uuid,
                    coinType,
                    if (isReadOnly) DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date())
                    else "FIO ${getCurrentBip44Index() + 2}",
                    Balance.getZeroBalance(coinType),
                    backing::updateAccountContext,
                    if (isReadOnly) 0 else getCurrentBip44Index() + 1)
        }
    }

    fun getBip44Path(account: FioAccount): HdKeyPath? =
            HdKeyPath.valueOf("m/44'/235'/${account.accountIndex}'/0/0")

    companion object {
        const val ID: String = "FIO"
    }
}

fun WalletManager.getFioAccounts() = getAccounts().filter { it is FioAccount && it.isVisible }
        .map { it as FioAccount }.sortedBy { it.accountIndex }

fun WalletManager.getActiveFioAccounts() = getAccounts()
        .filter { it is FioAccount && it.isVisible && it.isActive }
        .map { it as FioAccount }