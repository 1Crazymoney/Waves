package com.wavesplatform.protobuf.transaction
import com.google.protobuf.ByteString
import com.wavesplatform.account.{AddressScheme, PublicKey}
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.protobuf.utils.PBUtils
import com.wavesplatform.transaction.Asset
import com.wavesplatform.transaction.assets.exchange.OrderV1
import com.wavesplatform.{transaction => vt}

object PBOrders {
  import com.wavesplatform.protobuf.utils.PBImplicitConversions._

  def vanilla(order: PBOrder, version: Int = 0): VanillaOrder = {
    VanillaOrder(
      PublicKey(order.senderPublicKey.toByteArray),
      PublicKey(order.matcherPublicKey.toByteArray),
      vt.assets.exchange.AssetPair(Asset.fromProtoId(order.getAssetPair.getAmountAssetId), Asset.fromProtoId(order.getAssetPair.getPriceAssetId)),
      order.orderSide match {
        case PBOrder.Side.BUY             => vt.assets.exchange.OrderType.BUY
        case PBOrder.Side.SELL            => vt.assets.exchange.OrderType.SELL
        case PBOrder.Side.Unrecognized(v) => throw new IllegalArgumentException(s"Unknown order type: $v")
      },
      order.amount,
      order.price,
      order.timestamp,
      order.expiration,
      order.getMatcherFee.longAmount,
      order.proofs.map(_.toByteArray: ByteStr),
      if (version == 0) order.version.toByte else version.toByte,
      order.getMatcherFee.vanillaAssetId
    )
  }

  def vanillaV1(order: PBOrder): OrderV1 = vanilla(order, 1) match {
    case v1: OrderV1 => v1
    case _           => throw new IllegalArgumentException("OrderV1 required")
  }

  def protobuf(order: VanillaOrder): PBOrder = {
    PBOrder(
      chainId = AddressScheme.current.chainId,
      PBUtils.toByteStringUnsafe(order.senderPublicKey),
      PBUtils.toByteStringUnsafe(order.matcherPublicKey),
      Some(PBOrder.AssetPair(Some(order.assetPair.amountAsset.protoId), Some(order.assetPair.priceAsset.protoId))),
      order.orderType match {
        case vt.assets.exchange.OrderType.BUY  => PBOrder.Side.BUY
        case vt.assets.exchange.OrderType.SELL => PBOrder.Side.SELL
      },
      order.amount,
      order.price,
      order.timestamp,
      order.expiration,
      Some((order.matcherFeeAssetId, order.matcherFee)),
      order.version,
      order.proofs.map(bs => bs: ByteString)
    )
  }
}
