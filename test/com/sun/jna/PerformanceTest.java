/* Copyright (c) 2009 Timothy Wall, All Rights Reserved
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */
package com.sun.jna;

import junit.framework.*;
import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import java.lang.ref.*;
import java.io.File;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.sun.jna.DirectTest.TestInterface;
import com.sun.jna.DirectTest.TestLibrary;

//@SuppressWarnings("unused")
public class PerformanceTest extends TestCase implements Paths {

    public void testEmpty() { }

    private static class JNIMathLibrary {
        static {
            String path = TESTPATH + NativeLibrary.mapSharedLibraryName("testlib");;
            if (!new File(path).isAbsolute()) {
                path = new File(path).getAbsolutePath();
            }
            System.load(path);
        }
        
        private static native double cos(double x);
    }

    public static void main(java.lang.String[] argList) {
        checkPerformance();
    }

    static class MathLibrary {

        public static native double cos(double x);
        
        static {
            Native.register(Platform.MATH_LIBRARY_NAME);
        }
    }

    interface MathInterface extends Library {
        double cos(double x);
    }

    static class CLibrary {
        public static class size_t extends IntegerType {
            public size_t() {
                super(Native.POINTER_SIZE);
            }
            public size_t(long value) {
                super(Native.POINTER_SIZE, value);
            }
        }

        public static native Pointer memset(Pointer p, int v, size_t len);
        public static native Pointer memset(Pointer p, int v, int len);
        public static native Pointer memset(Pointer p, int v, long len);
        public static native long memset(long p, int v, long len);
        public static native int memset(int p, int v, int len);
        public static native int strlen(String s1);
        public static native int strlen(Pointer p);
        public static native int strlen(byte[] b);
        public static native int strlen(Buffer b);
        
        static {
            Native.register(Platform.C_LIBRARY_NAME);
        }
    }

    static interface CInterface extends Library {
        Pointer memset(Pointer p, int v, int len);
        int strlen(String s);
    }

    // Requires java.library.path include testlib
    public static void checkPerformance() {
        if (!Platform.HAS_BUFFERS) return;

        final int COUNT = 100000;
        System.out.println("Checking performance of different access methods (" + COUNT + " iterations)");
        final int SIZE = 8*1024;
        ByteBuffer b = ByteBuffer.allocateDirect(SIZE);
        // Native order is faster
        b.order(ByteOrder.nativeOrder());
        Pointer pb = Native.getDirectBufferPointer(b);

        String mname = Platform.MATH_LIBRARY_NAME;
        MathInterface mlib = (MathInterface)
            Native.loadLibrary(mname, MathInterface.class);
        Function f = NativeLibrary.getInstance(mname).getFunction("cos");

        ///////////////////////////////////////////
        // cos
        Object[] args = { new Double(0) };
        double dresult;
        long start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            dresult = mlib.cos(0d);
        }
        long delta = System.currentTimeMillis() - start;
        System.out.println("cos (JNA interface): " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            dresult = f.invokeDouble(args);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("cos (JNA function): " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            dresult = MathLibrary.cos(0d);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("cos (JNA direct): " + delta + "ms");

        long types = pb.peer;
        long cif;
        long resp;
        long argv;
        if (Native.POINTER_SIZE == 4) {
            b.putInt(0, (int)Structure.FFIType.get(double.class).peer);
            cif = Native.ffi_prep_cif(0, 1, Structure.FFIType.get(double.class).peer, types);
            resp = pb.peer + 4;
            argv = pb.peer + 12;
            double INPUT = 42;
            start = System.currentTimeMillis();
            for (int i=0;i < COUNT;i++) {
                b.putInt(12, (int)pb.peer + 16);
                b.putDouble(16, INPUT);
                Native.ffi_call(cif, f.peer, resp, argv);
                dresult = b.getDouble(4);
            }
            delta = System.currentTimeMillis() - start;
        }
        else {
            b.putLong(0, Structure.FFIType.get(double.class).peer);
            cif = Native.ffi_prep_cif(0, 1, Structure.FFIType.get(double.class).peer, types);
            resp = pb.peer + 8;
            argv = pb.peer + 16;
            double INPUT = 42;
            start = System.currentTimeMillis();
            for (int i=0;i < COUNT;i++) {
                b.putLong(16, pb.peer + 24);
                b.putDouble(24, INPUT);
                Native.ffi_call(cif, f.peer, resp, argv);
                dresult = b.getDouble(8);
            }
            delta = System.currentTimeMillis() - start;
        }
        System.out.println("cos (JNI ffi): " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            dresult = JNIMathLibrary.cos(0d);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("cos (JNI): " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            dresult = Math.cos(0d);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("cos (pure java): " + delta + "ms");

        ///////////////////////////////////////////
        // memset
        Pointer presult;
        String cname = Platform.C_LIBRARY_NAME;
        CInterface clib = (CInterface)
            Native.loadLibrary(cname, CInterface.class);
        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            presult = clib.memset(null, 0, 0);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("memset (JNA interface): " + delta + "ms");

        f = NativeLibrary.getInstance(cname).getFunction("memset");
        args = new Object[] { null, new Integer(0), new Integer(0)};
        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            presult = f.invokePointer(args);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("memset (JNA function): " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            presult = CLibrary.memset((Pointer)null, 0, new CLibrary.size_t(0));
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("memset (JNA direct Pointer/size_t): " + delta + "ms");
        start = System.currentTimeMillis();
        if (Native.POINTER_SIZE == 4) {
            for (int i=0;i < COUNT;i++) {
                presult = CLibrary.memset((Pointer)null, 0, 0);
            }
        }
        else {
            for (int i=0;i < COUNT;i++) {
                presult = CLibrary.memset((Pointer)null, 0, 0L);
            }
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("memset (JNA direct Pointer/primitive): " + delta + "ms");
        int iresult;
        long jresult;
        start = System.currentTimeMillis();
        if (Native.POINTER_SIZE == 4) {
            for (int i=0;i < COUNT;i++) {
                iresult = CLibrary.memset(0, 0, 0);
            }
        }
        else {
            for (int i=0;i < COUNT;i++) {
                jresult = CLibrary.memset(0L, 0, 0L);
            }
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("memset (JNA direct primitives): " + delta + "ms");

        if (Native.POINTER_SIZE == 4) {
            b.putInt(0, (int)Structure.FFIType.get(Pointer.class).peer);
            b.putInt(4, (int)Structure.FFIType.get(int.class).peer);
            b.putInt(8, (int)Structure.FFIType.get(int.class).peer);
            cif = Native.ffi_prep_cif(0, 3, Structure.FFIType.get(Pointer.class).peer, types);
            resp = pb.peer + 12;
            argv = pb.peer + 16;
            start = System.currentTimeMillis();
            for (int i=0;i < COUNT;i++) {
                b.putInt(16, (int)pb.peer + 28);
                b.putInt(20, (int)pb.peer + 32);
                b.putInt(24, (int)pb.peer + 36);
                b.putInt(28, 0);
                b.putInt(32, 0);
                b.putInt(36, 0);
                Native.ffi_call(cif, f.peer, resp, argv);
                b.getInt(12);
            }
            delta = System.currentTimeMillis() - start;
        }
        else {
            b.putLong(0, Structure.FFIType.get(Pointer.class).peer);
            b.putLong(8, Structure.FFIType.get(int.class).peer);
            b.putLong(16, Structure.FFIType.get(long.class).peer);
            cif = Native.ffi_prep_cif(0, 3, Structure.FFIType.get(Pointer.class).peer, types);
            resp = pb.peer + 24;
            argv = pb.peer + 32;
            start = System.currentTimeMillis();
            for (int i=0;i < COUNT;i++) {
                b.putLong(32, pb.peer + 56);
                b.putLong(40, pb.peer + 64);
                b.putLong(48, pb.peer + 72);
                b.putLong(56, 0);
                b.putInt(64, 0);
                b.putLong(72, 0);
                Native.ffi_call(cif, f.peer, resp, argv);
                b.getLong(24);
            }
            delta = System.currentTimeMillis() - start;
        }
        System.out.println("memset (JNI ffi): " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            Native.setMemory(0L, 0L, (byte)0);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("memset (JNI): " + delta + "ms");

        ///////////////////////////////////////////
        // strlen
        String str = "performance test";
        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            iresult = clib.strlen(str);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("strlen (JNA interface): " + delta + "ms");

        f = NativeLibrary.getInstance(cname).getFunction("strlen");
        args = new Object[] { str };
        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            iresult = f.invokeInt(args);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("strlen (JNA function): " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            iresult = CLibrary.strlen(str);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("strlen (JNA direct - String): " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            iresult = CLibrary.strlen(new NativeString(str).getPointer());
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("strlen (JNA direct - Pointer): " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            iresult = CLibrary.strlen(Native.toByteArray(str));
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("strlen (JNA direct - byte[]): " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            byte[] bytes = str.getBytes();
            b.position(0);
            b.put(bytes);
            b.put((byte)0);
            iresult = CLibrary.strlen(b);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("strlen (JNA direct - Buffer): " + delta + "ms");

        if (Native.POINTER_SIZE == 4) {
            b.putInt(0, (int)Structure.FFIType.get(Pointer.class).peer);
            cif = Native.ffi_prep_cif(0, 1, Structure.FFIType.get(int.class).peer, types);
            resp = pb.peer + 4;
            argv = pb.peer + 8;
            start = System.currentTimeMillis();
            for (int i=0;i < COUNT;i++) {
                b.putInt(8, (int)pb.peer + 12);
                b.putInt(12, (int)pb.peer + 16);
                b.position(16);
                // This operation is very expensive!
                b.put(str.getBytes());
                b.put((byte)0);
                Native.ffi_call(cif, f.peer, resp, argv);
                iresult = b.getInt(4);
            }
            delta = System.currentTimeMillis() - start;
        }
        else {
            b.putLong(0, Structure.FFIType.get(Pointer.class).peer);
            cif = Native.ffi_prep_cif(0, 1, Structure.FFIType.get(long.class).peer, types);
            resp = pb.peer + 8;
            argv = pb.peer + 16;
            start = System.currentTimeMillis();
            for (int i=0;i < COUNT;i++) {
                b.putLong(16, pb.peer + 24);
                b.putLong(24, pb.peer + 32);
                b.position(32);
                // This operation is very expensive!
                b.put(str.getBytes());
                b.put((byte)0);
                Native.ffi_call(cif, f.peer, resp, argv);
                jresult = b.getLong(8);
            }
            delta = System.currentTimeMillis() - start;
        }
        System.out.println("strlen (JNI ffi): " + delta + "ms");

        ///////////////////////////////////////////
        // Direct buffer vs. Pointer methods
        byte[] bulk = new byte[SIZE];
        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            b.putInt(0, 0);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("direct Buffer write: " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            b.position(0);
            b.put(bulk);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("direct Buffer write (bulk): " + delta + "ms");

        Pointer p = new Memory(SIZE);
        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            p.setInt(0, 0);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("Memory write: " + delta + "ms");

        start = System.currentTimeMillis();
        for (int i=0;i < COUNT;i++) {
            p.write(0, bulk, 0, bulk.length);
        }
        delta = System.currentTimeMillis() - start;
        System.out.println("Memory write (bulk): " + delta + "ms");

        ///////////////////////////////////////////
        // Callbacks
        TestInterface tlib = (TestInterface)Native.loadLibrary("testlib", TestInterface.class);
        start = System.currentTimeMillis();
        TestInterface.Int32Callback cb = new TestInterface.Int32Callback() {
            public int invoke(int arg1, int arg2) {
                return arg1 + arg2;
            }
        };
        tlib.callInt32CallbackRepeatedly(cb, 1, 2, COUNT);
        delta = System.currentTimeMillis() - start;
        System.out.println("callback (JNA interface): " + delta + "ms");

        tlib = new TestLibrary();
        start = System.currentTimeMillis();
        tlib.callInt32CallbackRepeatedly(cb, 1, 2, COUNT);
        delta = System.currentTimeMillis() - start;
        System.out.println("callback (JNA direct): " + delta + "ms");

        start = System.currentTimeMillis();
        TestInterface.NativeLongCallback nlcb = new TestInterface.NativeLongCallback() {
            public NativeLong invoke(NativeLong arg1, NativeLong arg2) {
                return new NativeLong(arg1.longValue() + arg2.longValue());
            }
        };
        tlib.callLongCallbackRepeatedly(nlcb, new NativeLong(1), new NativeLong(2), COUNT);
        delta = System.currentTimeMillis() - start;
        System.out.println("callback w/NativeMapped (JNA interface): " + delta + "ms");

        tlib = new TestLibrary();
        start = System.currentTimeMillis();
        tlib.callLongCallbackRepeatedly(nlcb, new NativeLong(1), new NativeLong(2), COUNT);
        delta = System.currentTimeMillis() - start;
        System.out.println("callback w/NativeMapped (JNA direct): " + delta + "ms");
    }
}

