package com.wavesplatform.transaction
import cats.Id
import com.wavesplatform.account.{Address, Alias}
import com.wavesplatform.block.{Block, MicroBlock}
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.lang.ValidationError
import com.wavesplatform.lang.v1.evaluator.Log
import com.wavesplatform.transaction.assets.exchange.Order

import scala.util.Either

object TxValidationError {
  type Validation[T] = Either[ValidationError, T]

  case class InvalidAddress(reason: String)                    extends ValidationError
  case class NegativeAmount(amount: Long, of: String)          extends ValidationError
  case class NonPositiveAmount(amount: Long, of: String)       extends ValidationError
  case class NegativeMinFee(minFee: Long, of: String)          extends ValidationError
  case class InsufficientFee(msg: String = "insufficient fee") extends ValidationError
  case object TooBigArray                                      extends ValidationError
  case object InvalidName                                      extends ValidationError
  case object InvalidAssetId                                   extends ValidationError
  case object OverflowError                                    extends ValidationError
  case object ToSelf                                           extends ValidationError
  case object MissingSenderPrivateKey                          extends ValidationError
  case object UnsupportedTransactionType                       extends ValidationError
  case object InvalidRequestSignature                          extends ValidationError
  case class BlockFromFuture(ts: Long)                         extends ValidationError
  case class AlreadyInTheState(txId: ByteStr, txHeight: Int)   extends ValidationError
  case class AccountBalanceError(errs: Map[Address, String])   extends ValidationError
  case class AliasDoesNotExist(a: Alias)                       extends ValidationError { override def toString: String = s"Alias '$a' does not exists." }
  case class AliasIsDisabled(a: Alias)                         extends ValidationError
  case class OrderValidationError(order: Order, err: String)   extends ValidationError
  case class SenderIsBlacklisted(addr: String)                 extends ValidationError
  case class Mistiming(err: String)                            extends ValidationError
  case class BlockAppendError(err: String, b: Block)           extends ValidationError
  case class ActivationError(err: String)                      extends ValidationError
  case class UnsupportedVersion(version: Int)                  extends ValidationError
  case class GenericError(err: String)                         extends ValidationError

  object GenericError {
    def apply(ex: Throwable): GenericError = new GenericError(ex.getMessage)
  }

  case class InvalidSignature(s: Signed, details: Option[InvalidSignature] = None) extends ValidationError {
    override def toString: String = s"InvalidSignature(${s.toString + " reason: " + details})"
  }

  trait HasScriptType extends ValidationError {
    def isAssetScript: Boolean
  }

  trait CanFailTransaction

  class ScriptExecutionError(val error: String, val log: Log[Id], override val isAssetScript: Boolean) extends ValidationError with HasScriptType {
    override def toString: String = {
      val target = if (isAssetScript) "Asset" else "Account"
      s"ScriptExecutionError(error = $error, type = $target, log =${logToString(log)})"
    }
  }

  object ScriptExecutionError {
    def apply(error: String, log: Log[Id], isAssetScript: Boolean): ScriptExecutionError =
      if (isAssetScript) new ScriptExecutionError(error, log, isAssetScript) with CanFailTransaction
      else new ScriptExecutionError(error, log, isAssetScript)

    def unapply(e: ScriptExecutionError): Option[(String, Log[Id], Boolean)] =
      Some((e.error, e.log, e.isAssetScript))
  }

  case class DAppExecutionError(error: String, log: Log[Id]) extends ValidationError with HasScriptType with CanFailTransaction {
    override def toString: String       = s"DAppExecutionError(error = $error, type = Account, log =${logToString(log)})"
    override val isAssetScript: Boolean = false
  }

  case class InsufficientInvokeActionFee(error: String) extends ValidationError with CanFailTransaction

  class TransactionNotAllowedByScript(val log: Log[Id], override val isAssetScript: Boolean) extends ValidationError with HasScriptType {
    override def toString: String = {
      val target = if (isAssetScript) "Asset" else "Account"
      s"TransactionNotAllowedByScript(type = $target, log =${logToString(log)})"
    }
  }

  object TransactionNotAllowedByScript {
    def apply(log: Log[Id], isAssetScript: Boolean): TransactionNotAllowedByScript =
      if (isAssetScript) new TransactionNotAllowedByScript(log, isAssetScript) with CanFailTransaction
      else new TransactionNotAllowedByScript(log, isAssetScript)

    def unapply(e: TransactionNotAllowedByScript): Option[(Log[Id], Boolean)] =
      Some((e.log, e.isAssetScript))
  }

  def logToString(log: Log[Id]): String =
    if (log.isEmpty) ""
    else {
      log
        .map {
          case (name, Right(v)) => s"$name = ${v.prettyString(1)}"
//          case (name, Right(v))          => s"$name = ${val str = v.toString; if (str.isEmpty) "<empty>" else v}"
          case (name, l @ Left(_)) => s"$name = $l"
        }
        .map("\t" + _)
        .mkString("\n", "\n", "\n")
    }

  case class MicroBlockAppendError(err: String, microBlock: MicroBlock) extends ValidationError {
    override def toString: String = s"MicroBlockAppendError($err, ${microBlock.totalResBlockSig} ~> ${microBlock.reference.trim}])"
  }

  case object EmptyDataKey extends ValidationError {
    override def toString: String = "Empty key found"
  }

  case object DuplicatedDataKeys extends ValidationError {
    override def toString: String = s"Duplicated keys found"
  }

  case class WrongChain(expected: Byte, provided: Byte) extends ValidationError {
    override def toString: String = s"Wrong chain-id. Expected - $expected, provided - $provided"
  }

  case class UnsupportedTypeAndVersion(typeId: Byte, version: Int) extends ValidationError {
    override def toString: String = s"Bad transaction type ($typeId) and version ($version)"
  }

  case class UsupportedProofVersion(version: Int, supported: List[Int]) extends ValidationError {
    override def toString: String = s"Unsupported proofs version - $version. Expected one of ${supported.mkString("[", ", ", "]")}"
  }

  case class TooManyProofs(max: Int, actual: Int) extends ValidationError {
    override def toString: String = s"Too many proofs ($actual), only $max allowed"
  }

  case class ToBigProof(max: Int, actual: Int) extends ValidationError {
    override def toString: String = s"Too large proof ($actual), must be max $max bytes"
  }
}
