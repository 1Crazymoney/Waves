package com.wavesplatform.lang.v1.evaluator.ctx.impl.waves

import cats.implicits._
import com.wavesplatform.common.utils.EitherExt2
import com.wavesplatform.lang.directives.values._
import com.wavesplatform.lang.directives.{DirectiveDictionary, DirectiveSet}
import com.wavesplatform.lang.v1.CTX
import com.wavesplatform.lang.v1.evaluator.ctx.impl.waves.Functions.{addressFromStringF, _}
import com.wavesplatform.lang.v1.evaluator.ctx.impl.waves.Types._
import com.wavesplatform.lang.v1.evaluator.ctx.impl.waves.Vals._
import com.wavesplatform.lang.v1.traits._

object WavesContext {
  def build(ds: DirectiveSet): CTX[Environment] =
    invariableCtx |+| variableCtxCache(ds)

  private val commonFunctions =
    Array(
      txHeightByIdF,
      getIntegerFromStateF,
      getBooleanFromStateF,
      getBinaryFromStateF,
      getStringFromStateF,
      addressFromRecipientF
    )

  private val balanceV123Functions =
    Array(
      assetBalanceF,
      wavesBalanceF
    )

  private val balanceV4Functions =
    Array(
      assetBalanceV4F,
      wavesBalanceV4F
    )

  private def selfCallFunctions(v: StdLibVersion) =
    Array(
      getIntegerFromStateSelfF,
      getBooleanFromStateSelfF,
      getBinaryFromStateSelfF,
      getStringFromStateSelfF,
      assetBalanceSelfF,
      wavesBalanceSelfF
    ) ++ extractedStateSelfFuncs(v)

  private val invariableCtx =
    CTX(Seq(), Map(height), commonFunctions)

  private val allDirectives =
    for {
      version     <- DirectiveDictionary[StdLibVersion].all
      scriptType  <- DirectiveDictionary[ScriptType].all
      contentType <- DirectiveDictionary[ContentType].all
    } yield DirectiveSet(version, scriptType, contentType)

  private val variableCtxCache: Map[DirectiveSet, CTX[Environment]] =
    allDirectives
      .filter(_.isRight)
      .map(_.explicitGet())
      .map(ds => (ds, variableCtx(ds)))
      .toMap

  private def variableCtx(ds: DirectiveSet): CTX[Environment] = {
    val isTokenContext = ds.scriptType match {
      case Account => false
      case Asset   => true
    }
    val proofsEnabled = !isTokenContext
    val version       = ds.stdLibVersion
    CTX(
      variableTypes(version, proofsEnabled),
      variableVars(isTokenContext, version, ds.contentType, proofsEnabled),
      variableFuncs(version, ds.scriptType, proofsEnabled)
    )
  }

  private def fromV3Funcs(proofsEnabled: Boolean, v: StdLibVersion) =
    extractedFuncs(v) ++ Array(
      assetInfoF(v),
      blockInfoByHeightF(v),
      transferTxByIdF(proofsEnabled, v),
      stringFromAddressF
    )

  private def fromV4Funcs(proofsEnabled: Boolean, version: StdLibVersion) =
    fromV3Funcs(proofsEnabled, version) ++
      Array(
        calculateAssetIdF,
        transactionFromProtoBytesF(proofsEnabled, version),
        simplifiedIssueActionConstructor,
        detailedIssueActionConstructor
      ) ++
      balanceV4Functions

  private def fromV5Funcs(proofsEnabled: Boolean, version: StdLibVersion) =
    fromV4Funcs(proofsEnabled, version)

  private def variableFuncs(version: StdLibVersion, scriptType: ScriptType, proofsEnabled: Boolean) = {
    val commonFuncs =
      Array(
        getIntegerFromArrayF(version),
        getBooleanFromArrayF(version),
        getBinaryFromArrayF(version),
        getStringFromArrayF(version),
        getIntegerByIndexF(version),
        getBooleanByIndexF(version),
        getBinaryByIndexF(version),
        getStringByIndexF(version),
        addressFromPublicKeyF(version),
        if (version >= V4) addressFromStringV4 else addressFromStringF(version)
      )

    val versionSpecificFuncs =
      version match {
        case V1 | V2                     => Array(txByIdF(proofsEnabled, version)) ++ balanceV123Functions
        case V3                          => fromV3Funcs(proofsEnabled, version) ++ balanceV123Functions
        case V4                          => fromV4Funcs(proofsEnabled, version)
        case V5 if scriptType == Account => fromV5Funcs(proofsEnabled, version) ++ selfCallFunctions(V5)
        case V5                          => fromV5Funcs(proofsEnabled, version)
      }
    commonFuncs ++ versionSpecificFuncs
  }

  private def variableVars(
      isTokenContext: Boolean,
      version: StdLibVersion,
      contentType: ContentType,
      proofsEnabled: Boolean
  ) = {
    val txVal = tx(isTokenContext, version, proofsEnabled)
    version match {
      case V1 => Map(txVal)
      case V2 => Map(sell, buy, txVal)
      case V3 | V4 | V5 =>
        val `this` = if (isTokenContext) assetThis(version) else accountThis
        val txO    = if (contentType == Expression) Map(txVal) else Map()
        val common = Map(sell, buy, lastBlock(version), `this`)
        common ++ txO
    }
  }

  private def variableTypes(version: StdLibVersion, proofsEnabled: Boolean) =
    buildWavesTypes(proofsEnabled, version) ++
      (if (version >= V3) dAppTypes(version) else Nil)
}
