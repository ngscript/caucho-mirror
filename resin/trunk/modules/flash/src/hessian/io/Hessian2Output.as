/*
 * Copyright (c) 2001-2008 Caucho Technology, Inc.  All rights reserved.
 *
 * The Apache Software License, Version 1.1
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Caucho Technology (http://www.caucho.com/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "Burlap", "Resin", and "Caucho" must not be used to
 *    endorse or promote products derived from this software without prior
 *    written permission. For written permission, please contact
 *    info@caucho.com.
 *
 * 5. Products derived from this software may not be called "Resin"
 *    nor may "Resin" appear in their names without prior written
 *    permission of Caucho Technology.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL CAUCHO TECHNOLOGY OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author Emil Ong
 */

package hessian.io
{
	import flash.errors.IllegalOperationError;
	import flash.utils.ByteArray;
	import flash.utils.Dictionary;
	import flash.utils.IDataOutput;
  import flash.utils.describeType;
  import flash.utils.getQualifiedClassName;

  import hessian.util.IntrospectionUtil;

  /**
   * A writer for the Hessian 2.0 protocol.
   */
  public class Hessian2Output extends AbstractHessianOutput
  {
    private const SIZE:int = 4096;

    private var _out:IDataOutput;
    private var _refs:Dictionary;

    private var _classRefs:Object;
    private var _numClassRefs:int = 0;

    private var _typeRefs:Dictionary;
    private var _numRefs:int = 0;

    private var _buffer:ByteArray = new ByteArray();

    private var _isStreaming:Boolean;

    /**
     * Creates a new HessianOutput.
     *
     * @param out The IDataOutput to which this HessianOutput will write.
     *
     * @see #init(IDataOutput)
     *
     */
    public function Hessian2Output(out:IDataOutput = null):void
    {
      init(out);
    }

    /**
     * Initialize the Hessian stream with the underlying IDataOutput.  This
     * method will reset the internal data of this instance, meaning this
     * HessianOutput may be reused.
     *
     * @param out The IDataOutput to which this HessianOutput will write.
     */
    public override function init(out:IDataOutput):void
    {
      _out = out;
      resetReferences();
    }

    /**
     * Starts the method call.
     *
     * <p>
     *   <code><pre>
     *   C
     *   string # method name
     *   int    # arg count
     *   </pre></code>
     * </p>
     *
     * @param method The method name to call.
     */
    public override function startCall(method:String, length:int):void
    {
      var offset:int = _buffer.position;

      if (SIZE < offset + 32) {
        flush();
        offset = _buffer.position; // XXX this doesn't make sense
      }

      var buffer:ByteArray = _buffer;

      buffer.writeByte('C'.charCodeAt());
      writeString(method);
      writeInt(length);
    }

    /**
     * Writes a generic object to the output stream.
     *
     * @param object The object to write.
     * @param className The name of the class to write to the stream.
     *                  May be the name of a primitive or user created class.
     */
    public override function writeObject(object:Object, 
                                         className:String = null):void
    {
      if (object == null) {
        writeNull();
        return;
      }

      if (object is Boolean || className == "Boolean") {
        writeBoolean(object as Boolean);
        return;
      }
      else if (className == "Number") {
        writeDouble(object as Number); // XXX should this be writeLong?
        return;
      }
      else if (className == "Double") {
        writeDouble(object as Number); // special hack for double
        return;
      }
      else if (className == "Long") {
        writeLong(object as Number); // special hack for long 
        return;
      }
      else if (object is int || className == "int") {
        writeInt(object as int);
        return;
      }
      else if (object is Number) {
        if (isNaN(Number(object))) {
          writeNull();
        } 
        else {
          writeDouble(object as Number); // XXX should this be writeLong?
        }
        return;
      }
      else if (object is Date || className == "Date") {
        writeUTCDate((object as Date).valueOf());
        return;
      }
      else if (object is Array || className == "Array") {
        if (addRef(object))
          return;

        var array:Array = object as Array;

        var hasEnd:Boolean = writeListBegin(array.length, className);

        for (var i:int = 0; i < array.length; i++)
          writeObject(array[i]);

        if (hasEnd)
          writeListEnd();

        return;
      }
      else if (object is String || className == "String") {
        writeString(object as String);
        return;
      }
      else if (object is ByteArray || className == "ByteArray") {
        writeBytes(object as ByteArray);
        return;
      }

      /* XXX: Figure out how to get a real associative array in AS that 
         associates objects by their value rather than their toString value
      if (addRef(object))
        return;*/

      // writeReplace not supported at this time
      // to save processing time

      var type:XML = describeType(object);

      if (type.@alias != null && type.@alias.length() != 0)
        className = type.@alias;
      else if (object.hasOwnProperty("hessianTypeName"))
        className = object.hessianTypeName;
      else {
        className = getQualifiedClassName(object) as String;
        className = className.replace("::", ".");
      }

      var def:ObjectDefinition = writeObjectBegin(className, type);
      writeInstance(object, def);
    }

    /**
      * Writes the definition of the object before writing the object itself,
      * which may be the explicit definition if the type has not yet been
      * encountered or it may just be a reference to the object if we have
      * seen it before.
      */
    public override function writeObjectBegin(className:String, 
                                              type:XML):ObjectDefinition
    {
      if (_classRefs == null)
        _classRefs = new Object();

      var ref:int = -1;
      var def:ObjectDefinition = _classRefs[className];

      if (def != null) {
        if (SIZE < _buffer.position + 32)
          flush();

        ref = def.ref;

        if (ref <= Hessian2Constants.OBJECT_DIRECT_MAX) {
          _buffer.writeByte(Hessian2Constants.BC_OBJECT_DIRECT + ref);
        }
        else {
          _buffer.writeByte(0);
          writeInt(ref);
        }
      }
      else {
        ref = _numClassRefs++;

        var fieldNames:Array = getFieldNames(type);
        def = new ObjectDefinition(className, fieldNames, ref);
        _classRefs[className] = def;

        if (SIZE < _buffer.position + 32)
          flush();

        _buffer.writeByte('C'.charCodeAt());

        writeString(className);

        def.write(this);
      }

      return def;
    }

    private function getFieldNames(type:XML):Array
    {
      var fieldNames:Array = new Array();
      var metadata:XMLList = null;
      var variables:XMLList = type.variable;
      var accessors:XMLList = type.accessor;	

      var key:String = null;

      for each(var variable:XML in variables) {
        metadata = variable.metadata;

        if (IntrospectionUtil.getMetadata(metadata, "Transient") != null)
          continue;

        key = variable.@name;

        if (key != "hessianTypeName")
          fieldNames.push(key);
      }

      // This is needed to handle Bindable properties:
      // they do not appear as variables, but rather as 
      // <accessor>'s with Bindable metadata children
      for each(var accessor:XML in accessors) {
        metadata = accessor.metadata;

        if (IntrospectionUtil.getMetadata(metadata, "Transient") != null)
          continue;

        if (IntrospectionUtil.getMetadata(metadata, "Bindable") != null) {
          key = accessor.@name;

          if (key != "hessianTypeName")
            fieldNames.push(key);
        }
      }

      return fieldNames;
    }

    private function writeInstance(instance:Object, def:ObjectDefinition):void
    {
      for each (var fieldName:String in def.fieldNames)
        writeObject(instance[fieldName]);

      /* 
         XXX per-instance variables
      for (key in obj) {
        if (key != "hessianTypeName")
          writeObject(instance[key]);
      }
      */
    }
    
    /**
     * Writes the list header to the stream.  List writers will call
     * <code>writeListBegin</code> followed by the list contents and then
     * call <code>writeListEnd</code>.
     *
     * <p>
     *   <code><pre>
     *   &lt;list>
     *     &lt;type>java.util.ArrayList&lt;/type>
     *     &lt;length>3&lt;/length>
     *     &lt;int>1&lt;/int>
     *     &lt;int>2&lt;/int>
     *     &lt;int>3&lt;/int>
     *   &lt;/list>
     *   </pre></code>
     * </p>
     * 
     * @param length The length of the list.
     * @param type The type of the elements in the list.
     *
     * @return If this list will have an end.
     */
    public override function writeListBegin(length:int, 
                                            type:String = null):Boolean
    {
      flushIfFull();

      if (length < 0) {
        if (type != null) {
          _buffer.writeByte(Hessian2Constants.BC_LIST_VARIABLE);
          writeType(type);
        }
        else 
          _buffer.writeByte(Hessian2Constants.BC_LIST_VARIABLE_UNTYPED);

        return true;
      }
      else if (length <= Hessian2Constants.LIST_DIRECT_MAX) {
        if (type != null) {
          _buffer.writeByte(Hessian2Constants.BC_LIST_DIRECT + length);
          writeType(type);
        }
        else 
          _buffer.writeByte(Hessian2Constants.BC_LIST_DIRECT_UNTYPED + length);

        return false;
      }
      else {
        if (type != null) {
          _buffer.writeByte(Hessian2Constants.BC_LIST_FIXED);
          writeType(type);
        }
        else 
          _buffer.writeByte(Hessian2Constants.BC_LIST_FIXED_UNTYPED);

        writeInt(length);

        return false;
      }
    }

    /**
     * Writes the tail of the list to the stream.
     */
    public override function writeListEnd():void
    {
      flushIfFull();

      _buffer.writeByte(Hessian2Constants.BC_END);
    }

    /**
     * Writes the map header to the stream.  Map writers will call
     * <code>writeMapBegin</code> followed by the map contents and then
     * call <code>writeMapEnd</code>.
     *
     * <p>
     *   <code><pre>
     *   Mt b16 b8 type (<key> <value>)z
     *   </pre></code>
     * </p>
     *
     * @param type The type of the map to write.
     */
    public override function writeMapBegin(type:String):void
    {
      if (SIZE < _buffer.position + 32)
        flush();

      if (type != null && type != "Object") {
        _buffer.writeByte(Hessian2Constants.BC_MAP);
        writeType(type);
      }
      else 
        _buffer.writeByte(Hessian2Constants.BC_MAP_UNTYPED);
    }

    /**
     * Writes the tail of the map to the stream.
     */
    public override function writeMapEnd():void
    {
      flushIfFull();

      _buffer.writeByte(Hessian2Constants.BC_END);
    }

    /**
     * <code><pre>
     * type ::= string
     *      ::= int
     * </code></pre>
     */
    private function writeType(type:String):void
    {
      flushIfFull();

      var len:int = type.length;

      if (len == 0)
        throw new IllegalOperationError("empty type is not allowed");

      if (_typeRefs == null)
        _typeRefs = new Dictionary();

      var typeRef:int = -1;
      var typeRefV:Object = _typeRefs[type];

      if (typeRefV != null) {
        typeRef = typeRefV as int;

        writeInt(typeRef);
      }
      else {
        typeRef = typeRefV as int;
        _typeRefs.put[type] = typeRef;

        writeString(type);
      }
    }

    /**
     * Writes a boolean value to the stream.  The boolean will be written
     * with the following syntax:
     *
     * <p>
     *   <code><pre>
     *   T
     *   F
     *   </pre></code>
     * </p>
     *
     * @param value The boolean value to write.
     */
    public override function writeBoolean(value:Boolean):void
    {
      if (SIZE < _buffer.position + 16)
        flush();

      if (value)
        _buffer.writeByte('T'.charCodeAt());
      else
        _buffer.writeByte('F'.charCodeAt());
    }

    /**
     * Writes an integer value to the stream.  The integer will be written
     * with the following syntax:
     *
     * <p>
     *   <code><pre>
     *   I b32 b24 b16 b8
     *   </pre></code>
     * </p>
     *
     * @param value The integer value to write.
     */
    public override function writeInt(value:int):void
    {
      if (SIZE < _buffer.position + 16)
        flush();

      if (Hessian2Constants.INT_DIRECT_MIN <= value && 
          Hessian2Constants.INT_DIRECT_MAX >= value) {
        _buffer.writeByte(value + Hessian2Constants.BC_INT_ZERO);
      }
      else if (Hessian2Constants.INT_BYTE_MIN <= value && 
               Hessian2Constants.INT_BYTE_MAX >= value) {
        _buffer.writeByte((value >> 8) + Hessian2Constants.BC_INT_BYTE_ZERO);
        _buffer.writeByte(value);
      }
      else if (Hessian2Constants.INT_SHORT_MIN <= value && 
               Hessian2Constants.INT_SHORT_MAX >= value) {
        _buffer.writeByte((value >> 16) + Hessian2Constants.BC_INT_SHORT_ZERO);
        _buffer.writeByte(value >> 8)
        _buffer.writeByte(value);
      }
      else {
        _buffer.writeByte('I'.charCodeAt());
        _buffer.writeByte(value >> 24);
        _buffer.writeByte(value >> 16);
        _buffer.writeByte(value >> 8);
        _buffer.writeByte(value);
      }
    }

    /**
     * Writes a long value to the stream.  The long will be written
     * with the following syntax:
     *
     * <p>
     *   <code><pre>
     *   L b64 b56 b48 b40 b32 b24 b16 b8
     *   </pre></code>
     * </p>
     *
     * @param value The long value to write.
     */
    public override function writeLong(value:Number):void
    {
      if (SIZE < _buffer.position + 16)
        flush();

      if (Hessian2Constants.LONG_DIRECT_MIN <= value && 
          Hessian2Constants.LONG_DIRECT_MAX >= value) {
        _buffer.writeByte(value + Hessian2Constants.BC_LONG_ZERO);
      }
      else if (Hessian2Constants.LONG_BYTE_MIN <= value && 
               Hessian2Constants.LONG_BYTE_MAX >= value) {
        _buffer.writeByte((value >> 8) + Hessian2Constants.BC_LONG_BYTE_ZERO);
        _buffer.writeByte(value);
      }
      else if (Hessian2Constants.LONG_SHORT_MIN <= value && 
               Hessian2Constants.LONG_SHORT_MAX >= value) {
        _buffer.writeByte((value >> 16) + Hessian2Constants.BC_LONG_SHORT_ZERO);
        _buffer.writeByte(value >> 8)
        _buffer.writeByte(value);
      }
      else if (-0x80000000 <= value && value <= 0x7fffffff) {
        _buffer.writeByte(Hessian2Constants.BC_LONG_INT);
        _buffer.writeByte(value >> 24);
        _buffer.writeByte(value >> 16);
        _buffer.writeByte(value >> 8);
        _buffer.writeByte(value);
      }
      else {
        _buffer.writeByte('L'.charCodeAt());
        _buffer.writeByte(value >> 56);
        _buffer.writeByte(value >> 48);
        _buffer.writeByte(value >> 40);
        _buffer.writeByte(value >> 32);
        _buffer.writeByte(value >> 24);
        _buffer.writeByte(value >> 16);
        _buffer.writeByte(value >> 8);
        _buffer.writeByte(value);
      }
    }

    /**
     * Writes a double value to the stream.  The double will be written
     * with the following syntax:
     *
     * <p>
     *   <code><pre>
     *   D b64 b56 b48 b40 b32 b24 b16 b8
     *   </pre></code>
     * </p>
     *
     * @param value The double value to write.
     */
    public override function writeDouble(value:Number):void
    {
      if (SIZE < _buffer.position + 16)
        flush();

      var intValue:int = int(value);

      if (intValue == value) {
        if (intValue == 0) {
          _buffer.writeByte(Hessian2Constants.BC_DOUBLE_ZERO);
          return;
        }
        else if (intValue == 1) {
          _buffer.writeByte(Hessian2Constants.BC_DOUBLE_ONE);
          return;
        }
        else if (-0x80 <= intValue && intValue < 0x80) {
          _buffer.writeByte(Hessian2Constants.BC_DOUBLE_BYTE);
          _buffer.writeByte(intValue);
          return;
        }
        else if (-0x8000 <= intValue && intValue < 0x8000) {
          _buffer.writeByte(Hessian2Constants.BC_DOUBLE_SHORT);
          _buffer.writeByte(intValue >> 8);
          _buffer.writeByte(intValue);
          return;
        }
      }

      var mills:int = int(value * 1000);

      if (0.001 * mills == value) {
        _buffer.writeByte(Hessian2Constants.BC_DOUBLE_MILL);
        _buffer.writeByte(mills >> 24);
        _buffer.writeByte(mills >> 16);
        _buffer.writeByte(mills >> 8);
        _buffer.writeByte(mills);

        return;
      }

      var bits:ByteArray = Double.doubleToLongBits(value);

      _buffer.writeByte('D'.charCodeAt());
      _buffer.writeBytes(bits);
    }

    /**
     * Writes a date to the stream.
     *
     * <p>
     *   <code><pre>
     *   date ::= d   b64 b56 b48 b40 b32 b24 b16 b8
     *        ::= x65 b32 b24 b16 b8
     *   </pre></code>
     * </p>
     *
     * @param time The date in milliseconds from the epoch in UTC
     */
    public override function writeUTCDate(time:Number):void
    {
      if (SIZE < _buffer.position + 16)
        flush();

      if (time % 60000 == 0) {
        // compact date ::= x65 b32 b24 b16 b8

        var minutes:Number = time / 60000;
        var shifted:Number = minutes / 0x80000000;

        if ((shifted == 0) || (shifted == -1)) {
          _buffer.writeByte(Hessian2Constants.BC_DATE_MINUTE);
          _buffer.writeByte(minutes >> 24);
          _buffer.writeByte(minutes >> 16);
          _buffer.writeByte(minutes >> 8);
          _buffer.writeByte(minutes);
        }

        return;
      }

      _buffer.writeByte(Hessian2Constants.BC_DATE);

      if (time >= 0) {
        _buffer.writeByte(time / 0x100000000000000);
        _buffer.writeByte(time / 0x1000000000000);
        _buffer.writeByte(time / 0x10000000000);
        _buffer.writeByte(time / 0x100000000);
        _buffer.writeByte(time >> 24);
        _buffer.writeByte(time >> 16);
        _buffer.writeByte(time >> 8);
        _buffer.writeByte(time);
      }
      else {
        var abs:Number = Math.abs(time);

        var msi:Number = 0x100000000 - Math.abs(abs / 0x100000000);
        if (msi == 0x100000000)
          msi = 0xFFFFFFFF;

        var lsi:Number = 0x100000000 - (abs % 0x100000000);

        _buffer.writeByte(msi >> 24);
        _buffer.writeByte(msi >> 16);
        _buffer.writeByte(msi >> 8);
        _buffer.writeByte(msi);

        _buffer.writeByte(lsi >> 24);
        _buffer.writeByte(lsi >> 16);
        _buffer.writeByte(lsi >> 8);
        _buffer.writeByte(lsi);
      }
    }

    /**
     * Writes a null value to the stream.
     * The null will be written with the following syntax
     *
     * <p>
     *   <code><pre>
     *   N
     *   </pre></code>
     * </p>
     */
    public override function writeNull():void
    {
      if (SIZE < _buffer.position + 16)
        flush();

      _out.writeByte('N'.charCodeAt());
    }

    /**
     * Writes a string value to the stream using UTF-8 encoding.
     * The string will be written with the following syntax:
     *
     * <p>
     *   <code><pre>
     *   S b16 b8 string-value
     *   </pre></code>
     * </p>
     *
     * <p>
     *   If the value is null, it will be written as
     *
     *   <code><pre>
     *   N
     *   </pre></code>
     * </p>
     *
     * @param value The string value to write.
     * @param offset The offset in the string of where to start.
     * @param length The length in the string to write.
     */
    public override function writeString(value:*, 
                                         offset:int = 0, 
                                         length:int = 0):void
    {
      if (SIZE < _buffer.position + 16)
        flush();

      if (value == null)
        _out.writeByte('N'.charCodeAt());

      else {
        length = value.length;
        offset = 0;

        while (length > 0x8000) {
          var sublen:int = 0x8000;

          if (SIZE < _buffer.position + 16)
            flush();

          // chunk can't end in high surrogate
          var tail:int = value.charCodeAt(offset + sublen - 1);

          if (0xd800 <= tail && tail <= 0xdbff)
            sublen--;

          _buffer.writeByte(Hessian2Constants.BC_STRING_CHUNK);
          _buffer.writeByte(sublen >> 8);
          _buffer.writeByte(sublen);

          printString(value, offset, sublen);

          length -= sublen;
          offset += sublen;
        }

        if (SIZE < _buffer.position + 16)
          flush();

        if (length <= Hessian2Constants.STRING_DIRECT_MAX) {
          _buffer.writeByte(Hessian2Constants.BC_STRING_DIRECT + length);
        }
        else if (length <= Hessian2Constants.STRING_SHORT_MAX) {
          _buffer.writeByte(Hessian2Constants.BC_STRING_SHORT + (length >> 8));
          _buffer.writeByte(length);
        }
        else {
          _buffer.writeByte('S'.charCodeAt());
          _buffer.writeByte(length >> 8);
          _buffer.writeByte(length);
        }

        printString(value, offset, length);
      }
    }

    /**
     * Writes a byte array to the stream.
     * The array will be written with the following syntax:
     *
     * <p>
     *   <code><pre>
     *   B b16 b18 bytes
     *   </pre></code>
     * </p>
     *
     * <p>
     *   If the value is null, it will be written as
     *
     *   <code><pre>
     *   N
     *   </pre></code>
     * </p>
     *
     * @param buffer The byte buffer value to write.
     * @param offset The offset in the array of where to start.
     * @param length The length in the array to write.
     */
    public override function writeBytes(buffer:ByteArray, 
                                        offset:int = 0, 
                                        length:int = -1):void
    {
      if (buffer == null) {
        if (SIZE < _buffer.position + 16)
          flush();

        _buffer.writeByte('N'.charCodeAt());
      }
      else {
        flush();

        while (SIZE - _buffer.position - 3 < length) {
          var sublen:int = SIZE - _buffer.position - 3;

          if (sublen < 16) {
            flushBuffer();

            if (length < sublen)
              sublen = length;
          }

          _buffer.writeByte(Hessian2Constants.BC_BINARY_CHUNK);
          _buffer.writeByte(sublen >> 8);
          _buffer.writeByte(sublen);

          _buffer.writeBytes(buffer, offset, sublen);

          length -= sublen;
          offset += sublen;

          flushBuffer();
        }

        if (SIZE < _buffer.position + 16)
          flushBuffer();

        if (length <= Hessian2Constants.BINARY_DIRECT_MAX) {
          _buffer.writeByte(Hessian2Constants.BC_BINARY_DIRECT + length);
        }
        else if (length <= Hessian2Constants.BINARY_SHORT_MAX) {
          _buffer.writeByte(Hessian2Constants.BC_BINARY_SHORT + (length >> 8));
          _buffer.writeByte(length);
        }
        else {
          _buffer.writeByte('B'.charCodeAt());
          _buffer.writeByte(length >> 8);
          _buffer.writeByte(length);
        }

        _buffer.writeBytes(buffer, offset, length);
      }
    }
  
    /**
     * Writes a reference.
     *
     * <p>
     *   <code><pre>
     *   R b32 b24 b16 b8
     *   </pre></code>
     * </p>
     *
     * @param value The integer value to write.
     */
    public override function writeRef(value:int):void
    {
      if (SIZE < _buffer.position + 16)
        flush();

      _buffer.writeByte(Hessian2Constants.BC_REF);
      writeInt(value);
    }

    /**
     * Adds an object to the reference list.  If the object already exists,
     * writes the reference, otherwise, the caller is responsible for
     * the serialization.
     *
     * <p>
     *   <code><pre>
     *   R b32 b24 b16 b8
     *   </pre></code>
     * </p>
     *
     * @param object The object to add as a reference.
     *
     * @return true if the object has already been written.
     */
    public override function addRef(object:Object):Boolean
    {
      if (_refs == null)
        _refs = new Dictionary();

      var ref:Object = _refs[object];

      if (ref != null) {
        var value:int = ref as int;

        writeRef(value);
        return true;
      }
      else {
        _refs[object] = _numRefs++;

        return false;
      }
    }

    /**
     * Resets the references for streaming.
     */
    public override function resetReferences():void
    {
      if (_refs != null) {
        _refs = new Dictionary();
      }
    }

    /**
     * Removes a reference.
     *
     * @param obj The object which has a reference.
     * 
     * @return true if a reference was present for the given object.
     */
    public override function removeRef(obj:Object):Boolean
    {
      if (_refs != null) {
        delete _refs[obj];

        return true;
      }
      else
        return false;
    }

    /**
     * Replaces a reference from one object to another.
     * 
     * @param oldObj The object which has a reference to replace.
     * @param newObj The object to which to assign the reference.
     *
     * @return true if a reference was present for the given object.
     */
    public override function replaceRef(oldRef:Object, newRef:Object):Boolean
    {
      var value:Object = _refs[oldRef];

      if (value != null) {
        delete _refs[oldRef];
        _refs[newRef] = value;
        return true;
      }
      else
        return false;
    }

    /**
     * Prints a string to the stream, encoded as UTF-8 with preceeding length.
     *
     * @param value The string value to write.
     * @param offset The offset in the string of where to start.
     * @param length The length in the string to write.
     */
    public function printLenString(value:String):void
    {
      if (SIZE < _buffer.position + 16)
        flush();

      if (value == null) {
        _buffer.writeByte(0);
        _buffer.writeByte(0);
      }
      else {
        var len:int = value.length;
        _buffer.writeByte(len >> 8);
        _buffer.writeByte(len);

        printString(value, 0, len);
      }
    }

    /**
     * Prints a string to the stream, encoded as UTF-8.
     *
     * @param value The string value to write.
     * @param offset The offset in the string of where to start.
     * @param length The length in the string to write.
     */
    public function printString(value:String, 
                                offset:int = 0, 
                                length:int = -1):void
    {
      if (length < 0)
        length = value.length;

      for (var i:int = 0; i < length; i++) {
        if (SIZE < _buffer.position + 16)
          flush();

        var ch:int = value.charCodeAt(i + offset);

        if (ch < 0x80)
          _buffer.writeByte(ch);

        else if (ch < 0x800) {
          _buffer.writeByte(0xc0 + ((ch >> 6) & 0x1f));
          _buffer.writeByte(0x80 + (ch & 0x3f));
        }

        else {
          _buffer.writeByte(0xe0 + ((ch >> 12) & 0xf));
          _buffer.writeByte(0x80 + ((ch >> 6) & 0x3f));
          _buffer.writeByte(0x80 + (ch & 0x3f));
        }
      }
    }

    private function flushIfFull():void
    {
      if (SIZE < _buffer.position + 32) {
        _buffer.position = 0;
        _out.writeBytes(_buffer);
      }
    }

    public function flush():void
    {
      flushBuffer();
    }

    public function flushBuffer():void
    {
      _out.writeBytes(_buffer);
    }

    public function close():void
    {
      flush();
    }
  }
}
