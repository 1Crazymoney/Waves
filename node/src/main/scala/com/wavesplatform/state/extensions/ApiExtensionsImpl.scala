package com.wavesplatform.state.extensions

import com.wavesplatform.account.Address
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.database.LevelDBWriter
import com.wavesplatform.database.extensions.impl.{LevelDBWriterAddressTransactions, LevelDBWriterDistributions}
import com.wavesplatform.lang.ValidationError
import com.wavesplatform.state.extensions.impl.{CompositeAddressTransactions, CompositeDistributions}
import com.wavesplatform.state.reader.CompositeBlockchain
import com.wavesplatform.state.{AssetDistribution, AssetDistributionPage, Blockchain, Height, Portfolio}
import com.wavesplatform.transaction.assets.IssueTransaction
import com.wavesplatform.transaction.{Asset, Transaction, TransactionParser}
import monix.reactive.Observable

object ApiExtensionsImpl {
  def fromLevelDB(ldb: LevelDBWriter): ApiExtensions =
    fromAddressTransactionsAndDistributions(LevelDBWriterAddressTransactions(ldb), LevelDBWriterDistributions(ldb))

  def fromCompositeBlockchain(cb: CompositeBlockchain): ApiExtensions = {
    val baseAddressTransactions = LevelDBWriterAddressTransactions(cb.stableBlockchain.asInstanceOf[LevelDBWriter])
    val baseDistributions       = LevelDBWriterDistributions(cb.stableBlockchain.asInstanceOf[LevelDBWriter])
    val addressTransactions     = new CompositeAddressTransactions(baseAddressTransactions, () => cb.maybeDiff)
    val distributions           = new CompositeDistributions(cb, baseDistributions, () => cb.maybeDiff)
    fromAddressTransactionsAndDistributions(addressTransactions, distributions)
  }

  def apply(b: Blockchain): ApiExtensions = b match {
    case ldb: LevelDBWriter => fromLevelDB(ldb)
    case cb: CompositeBlockchain => fromCompositeBlockchain(cb)
    case _ => fromAddressTransactionsAndDistributions(AddressTransactions.empty, Distributions.empty)
  }

  private[extensions] def fromAddressTransactionsAndDistributions(at: AddressTransactions, d: Distributions): ApiExtensions = {
    new AddressTransactions with Distributions {
      override def portfolio(a: Address): Portfolio                               = d.portfolio(a)
      override def assetDistribution(asset: Asset.IssuedAsset): AssetDistribution = d.assetDistribution(asset)
      override def assetDistributionAtHeight(
          asset: Asset.IssuedAsset,
          height: Int,
          count: Int,
          fromAddress: Option[Address]
      ): Either[ValidationError, AssetDistributionPage] =
        d.assetDistributionAtHeight(asset, height, count, fromAddress)
      override def wavesDistribution(height: Int): Either[ValidationError, Map[Address, Long]]                    = d.wavesDistribution(height)
      override def nftObservable(address: Address, from: Option[Asset.IssuedAsset]): Observable[IssueTransaction] = d.nftObservable(address, from)
      override def addressTransactionsObservable(
          address: Address,
          types: Set[TransactionParser],
          fromId: Option[ByteStr]
      ): Observable[(Height, Transaction)] =
        at.addressTransactionsObservable(address, types, fromId)
    }
  }
}
