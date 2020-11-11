package com.wavesplatform.consensus

import com.wavesplatform.state.Blockchain
import com.wavesplatform.transaction.Asset.Waves
import com.wavesplatform.transaction.smart.{ContinuationTransaction, InvokeScriptTransaction}
import com.wavesplatform.transaction.{Authorized, Transaction}

object TransactionsOrdering {
  trait WavesOrdering extends Ordering[Transaction] {
    def isWhitelisted(t: Transaction): Boolean = false
    def txTimestampOrder(ts: Long): Long
    private def orderBy(t: Transaction): (Boolean, Double, Long, Long) = {
      val byWhiteList = !isWhitelisted(t) // false < true
      val size        = t.bytes().length
      val commonFee   = if (t.assetFee._1 != Waves) 0 else -t.assetFee._2
      val extraFee = t match {
        case i: InvokeScriptTransaction if i.assetFee._1 == Waves => -i.extraFeePerStep
        case _                                                    => 0
      }
      val byFee       = commonFee + extraFee
      val byTimestamp = txTimestampOrder(t.timestamp)

      (byWhiteList, byFee.toDouble / size.toDouble, byFee, byTimestamp)
    }
    override def compare(first: Transaction, second: Transaction): Int = {
      import Ordering.Double.TotalOrdering
      implicitly[Ordering[(Boolean, Double, Long, Long)]].compare(orderBy(first), orderBy(second))
    }
  }

  object InBlock extends WavesOrdering {
    // sorting from network start
    override def txTimestampOrder(ts: Long): Long = -ts
  }

  case class InUTXPool(whitelistAddresses: Set[String], blockchain: Blockchain) extends WavesOrdering {
    override def isWhitelisted(t: Transaction): Boolean =
      t match {
        case _ if whitelistAddresses.isEmpty                                                            => false
        case a: Authorized if whitelistAddresses.contains(a.sender.toAddress.stringRepr)                => true
        case i: InvokeScriptTransaction if whitelistAddresses.contains(i.dAppAddressOrAlias.stringRepr) => true
        case c: ContinuationTransaction                                                                 => isWhitelisted(c.resolveInvoke(blockchain)._2)
        case _                                                                                          => false
      }
    override def txTimestampOrder(ts: Long): Long = ts
  }
}
