package com.wavesplatform.database

import java.nio.ByteBuffer

import com.google.common.primitives.{Bytes, Ints, Longs, Shorts}
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.state.{AddressId, Height, TxNum}

//noinspection ScalaStyle,TypeAnnotation
object KeyHelpers {
  def h(prefix: Short, height: Int) =
    ByteBuffer.allocate(6).putShort(prefix).putInt(height).array()

  def hBytes(prefix: Short, bytes: Array[Byte], height: Int) =
    ByteBuffer.allocate(6 + bytes.length).putShort(prefix).put(bytes).putInt(height).array()

  def bytes(prefix: Short, bytes: Array[Byte]) =
    ByteBuffer.allocate(2 + bytes.length).putShort(prefix).put(bytes).array()

  def addr(prefix: Short, addressId: Long) = bytes(prefix, AddressId.toBytes(addressId))

  def hash(prefix: Short, hashBytes: ByteStr) = bytes(prefix, hashBytes.arr)

  def hAddr(prefix: Short, addressId: Long, height: Int) = hBytes(prefix, AddressId.toBytes(addressId), height)

  def hNum(prefix: Short, height: Int, num: TxNum) = Bytes.concat(Shorts.toByteArray(prefix), Ints.toByteArray(height), Shorts.toByteArray(num))

  def assetId(height: Height, txNum: TxNum) =
    ByteBuffer.allocate(6).putInt(height).putShort(txNum).array()

  def addressAndAsset(addressId: AddressId, height: Height, txNum: TxNum) =
    ByteBuffer
      .allocate(10)
      .putInt(addressId.toInt)
      .putInt(height)
      .putShort(txNum)
      .array()

  def addrBytes(addressId: Long, bytes: Array[Byte] = Array.emptyByteArray) = Bytes.concat(AddressId.toBytes(addressId), bytes)

  def historyKey(name: String, prefix: Short, b: Array[Byte]) = Key(name, bytes(prefix, b), readIntSeq, writeIntSeq)

  def intKey(name: String, prefix: Short, default: Int = 0): Key[Int] =
    Key(name, Shorts.toByteArray(prefix), Option(_).fold(default)(Ints.fromByteArray), Ints.toByteArray)

  def longKey(name: String, prefix: Short, default: Long = 0): Key[Long] =
    Key(name, Longs.toByteArray(prefix), Option(_).fold(default)(Longs.fromByteArray), Longs.toByteArray)

  def bytesSeqNr(name: String, prefix: Short, b: Array[Byte], default: Int = 0): Key[Int] =
    Key(name, bytes(prefix, b), Option(_).fold(default)(Ints.fromByteArray), Ints.toByteArray)

  def unsupported[A](message: String): A => Array[Byte] = _ => throw new UnsupportedOperationException(message)
}
