/*
 * Copyright 2014 Higher Frequency Trading
 *
 * http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.lang;

import net.openhft.lang.io.NativeBytes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author peter.lawrey
 */
public enum Jvm {
    ;

    static {
        boolean success = false;
        try {

            Field vm_supports_long_cas = AtomicLong.class.getDeclaredField("VM_SUPPORTS_LONG_CAS");
            vm_supports_long_cas.setAccessible(true);
            success = (Boolean) vm_supports_long_cas.get(null);
        } catch (Exception e) {
            // do nothing
        }
        VM_SUPPORTS_LONG_CAS = success;
    }

    public static final String TMP = System.getProperty("java.io.tmpdir");
    private static final boolean IS64BIT = is64Bit0();

    public static boolean is64Bit() {
        return IS64BIT;
    }

    private static boolean is64Bit0() {
        String systemProp;
        systemProp = System.getProperty("com.ibm.vm.bitmode");
        if (systemProp != null) {
            return "64".equals(systemProp);
        }
        systemProp = System.getProperty("sun.arch.data.model");
        if (systemProp != null) {
            return "64".equals(systemProp);
        }
        systemProp = System.getProperty("java.vm.version");
        return systemProp != null && systemProp.contains("_64");
    }

    private final static boolean VM_SUPPORTS_LONG_CAS;


    private static final int PROCESS_ID = getProcessId0();

    public static int getProcessId() {
        return PROCESS_ID;
    }

    private static int getProcessId0() {
        String pid = null;
        final File self = new File("/proc/self");
        try {
            if (self.exists()) {
                pid = self.getCanonicalFile().getName();
            }
        } catch (IOException ignored) {
        }

        if (pid == null) {
            pid = ManagementFactory.getRuntimeMXBean().getName().split("@", 0)[0];
        }

        if (pid == null) {
            int rpid = new Random().nextInt(1 << 16);
            LoggerHolder.LOGGER.log(Level.WARNING, "Unable to determine PID, picked a random number=" + rpid);

            return rpid;
        } else {
            return Integer.parseInt(pid);
        }
    }

    public static boolean vmSupportsCS8() {
        return VM_SUPPORTS_LONG_CAS;
    }

    /**
     * This may or may not be the OS thread id, but should be unique across processes
     *
     * @return a unique tid of up to 48 bits.
     */
    public static long getUniqueTid() {
        return getUniqueTid(Thread.currentThread());
    }

    public static long getUniqueTid(Thread thread) {
        // Assume 48 bit for 16 to 24-bit process id and 16 million threads from the start.
        return ((long) getProcessId() << 24) | thread.getId();
    }

    private static final String OS = System.getProperty("os.name").toLowerCase();

    public static boolean isWindows() {
        return OS.startsWith("win");
    }

    public static boolean isMacOSX() {
        return OS.contains("mac");
    }

    public static boolean isLinux() {
        return OS.startsWith("linux");
    }

    public static boolean isUnix() {
        return OS.contains("nix") ||
               OS.contains("nux") ||
               OS.contains("aix") ||
               OS.contains("bsd") ||
               OS.contains("sun") ||
               OS.contains("hpux");
    }

    public static boolean isSolaris() {
        return OS.startsWith("sun");
    }

    public static final int PID_BITS = Maths.intLog2(getPidMax());

    public static long getPidMax() {
        if (isLinux()) {
            File file = new File("/proc/sys/kernel/pid_max");
            if (file.canRead()) {
                try {
                    return Maths.nextPower2(new Scanner(file).nextLong(), 1);
                } catch (FileNotFoundException e) {
                    LoggerHolder.LOGGER.log(Level.WARNING, "", e);
                }
            }
        } else if (isMacOSX()) {
            return 1L << 24;
        }

        // the default.
        return 1L << 16;
    }

    private static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public static long freePhysicalMemoryOnWindowsInBytes() throws IOException {
        if (!isWindows()) {
            throw new IllegalStateException("Method freePhysicalMemoryOnWindowsInBytes() should " +
                "be called only on windows. Use Jvm.isWindows() to check the OS.");
        }

        Process pr = Runtime.getRuntime().exec("wmic OS get FreePhysicalMemory /Value");
        try {
            int result = pr.waitFor();
            String output = convertStreamToString(pr.getInputStream());
            if (result != 0) {
                String errorOutput = convertStreamToString(pr.getErrorStream());
                throw new IOException("Couldn't get free physical memory on windows. " +
                        "Command \"wmic OS get FreePhysicalMemory /Value\" exited with " +
                        result + " code, putput: \"" + output + "\", error output: \"" +
                        errorOutput + "\"");
            }
            String[] parts = output.trim().split("=");
            if (parts.length != 2) {
                throw new IOException("Couldn't get free physical memory on windows. " +
                        "Command \"wmic OS get FreePhysicalMemory /Value\" output has unexpected " +
                        "format: \"" + output + "\"");
            }
            try {
                return MemoryUnit.KILOBYTES.toBytes(Long.parseLong(parts[1]));
            } catch (NumberFormatException e) {
                throw new IOException("Couldn't get free physical memory on windows. " +
                        "Command \"wmic OS get FreePhysicalMemory /Value\" output has unexpected " +
                        "format: \"" + output + "\"", e);
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    public static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedRuntimeException();
    }


    /**
     * Utility method to support throwing checked exceptions out of the streams API
     * @param t the exception to rethrow
     * @return the exception
     */
    public static RuntimeException rethrow(Throwable t) {
        NativeBytes.UNSAFE.throwException(t);
        throw new AssertionError();
    }

    static class LoggerHolder {
        public static final Logger LOGGER = Logger.getLogger(Jvm.class.getName());
    }
}
