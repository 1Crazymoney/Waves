package com.wavesplatform.matcher.model

import com.wavesplatform.matcher.model.MatcherModel.{OrderId, Price}
import play.api.libs.json.{JsValue, Json}
import scorex.transaction.assets.exchange.{Order, OrderCancelTransaction, OrderType}

object MatcherModel {
  type Price = Long
  type Level[+A] = Vector[A]
  type OrderId = String
}

case class LevelAgg(price: Long, amount: Long)

sealed trait LimitOrder {
  def price: Price
  def amount: Long
  def order: Order
  def partial(amount: Long): LimitOrder

  def getSpendAmount: Long
  def getReceiveAmount: Long
  def feeAmount: Long = (BigInt(amount) * order.matcherFee  / order.amount).toLong
}

case class BuyLimitOrder(price: Price, amount: Long, order: Order) extends LimitOrder {
  def partial(amount: Price): LimitOrder = copy(amount = amount)
  def getReceiveAmount: Long = (BigInt(amount) * Order.PriceConstant / price).longValue()
  def getSpendAmount: Long = amount
}
case class SellLimitOrder(price: Price, amount: Long, order: Order) extends LimitOrder {
  def partial(amount: Price): LimitOrder = copy(amount = amount)
  def getSpendAmount: Long = (BigInt(amount) * Order.PriceConstant / price).longValue()
  def getReceiveAmount: Long = amount
}


object LimitOrder {
  sealed trait OrderStatus {
    def json: JsValue
  }
  case object Accepted extends OrderStatus {
    def json = Json.obj("status" -> "Accepted")
  }
  case object NotFound extends OrderStatus {
    def json = Json.obj("status" -> "NotFound")
  }
  case class PartiallyFilled(filledAmount: Long) extends OrderStatus {
    def json = Json.obj("status" -> "PartiallyFilled", "filledAmount" -> filledAmount)
  }
  case object Filled extends OrderStatus {
    def json = Json.obj("status" -> "Filled")
  }
  case object Cancelled extends OrderStatus {
    def json = Json.obj("status" -> "Cancelled")
  }

  def apply(o: Order): LimitOrder = o.orderType match {
    case OrderType.BUY => BuyLimitOrder(o.price, o.amount, o).copy()
    case OrderType.SELL => SellLimitOrder(o.price, o.amount, o)
  }

}

object Events {
  sealed trait Event
  case class OrderExecuted(submittedOrder: LimitOrder, counterOrder: LimitOrder) extends Event {
    def counterRemaining: Long = math.max(counterOrder.amount - submittedOrder.amount, 0)
    def submittedRemaining: Long = math.max(submittedOrder.amount - counterOrder.amount, 0)
    def executedAmount: Long = math.min(submittedOrder.amount, counterOrder.amount)
    def submittedExecuted = submittedOrder.partial(amount = executedAmount)
    def counterExecuted = counterOrder.partial(amount = executedAmount)
  }
  @SerialVersionUID(-3697114578758882607L)
  case class OrderAdded(order: LimitOrder) extends Event
  case class OrderCanceled(limitOrder: LimitOrder) extends Event
  case class OrderCancelRejected(id: OrderId, reason: String) extends Event
}
