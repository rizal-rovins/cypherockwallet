package jp.sugnakys.usbserialconsole.utils;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;

/**
 * Created by rohanagarwal94 on 2/8/17.
 */
public class Utils {
    private Utils() {
    }

    //TODO : hash160 hardware wallet public address - scriptpubkey, version, no.of inputs - vin_size, trans hsh - hash, previous output index - n in outputs, script length 19,

    public static int[] reverseIntArray(int[] arr) {

        final int len = arr.length;
        for (int i=0; i < (len/2); i++) {
            arr[i] += arr[len - 1 - i]; //  a = a+b
            arr[len - 1 - i] = arr[i] - arr[len - 1 - i];   //  b = a-b
            arr[i] -= arr[len - 1 - i]; //  a = a-b
        }

        return arr;
    }

    public static int[] bytearray2intarray(byte[] barray)
    {
        int[] iarray = new int[barray.length];
        int i = 0;
        for (byte b : barray)
            iarray[i++] = b & 0xff;
        return iarray;
    }

    public static byte[] hexStringToByteArray(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(s.substring(index, index + 2), 16);
            b[i] = (byte) v;
        }
        return b;
    }

    public static byte[] getHash160FromAddress(String address) {
        NetworkParameters main = TestNet3Params.get();

        // The string address is just a number expressed in base 58
        // Let us parse this string and see the bytes of the underlying number
        // 0:
        // -90:-57:12:74:-120:32:80:101:-63:-45:59:23:-63:86:19:127:-88:-57:54:-63:
        // -86:-114:24:124:
        byte[] full = Base58.decode(address);
        for (int i = 0; i < full.length; ++i) {
            System.out.print(full[i]+ ":");
        }

        // Let us compute the hash 160 of the address
        //-90:-57:12:74:-120:32:80:101:-63:-45:59:23:-63:86:19:127:-88:-57:54:-63:
        // These are the same 20 bytes as before
        Address addr = Address.fromBase58(main, address);
        System.out.print("\n");
        byte[] hash = addr.getHash160();
        for(int i = 0; i < hash.length; ++i){
            System.out.print(hash[i]+ ":");
        }



//        // Let us see the first 21 bytes of the address
//        // 0:-90:-57:12:74:-120:32:80:101:-63:-45:59:23:-63:86:19:127:-88:-57:54:-63:
//        byte[] decode = Base58.decodeChecked(address);
//        System.out.print("\n");
//        for(int i = 0; i < decode.length; ++i){
//            System.out.print(decode[i]+ ":");
//        }
//
//        // Let us compute the double sha256 hash of the first 21 bytes of the address
//        // and display the first 4 bytes: -86:-114:24:124:
//        // These first 4 bytes are exactly the last 4 bytes of the address above
//        byte[] check = Sha256Hash.hashTwice(decode);
//        System.out.print("\n");
//        for(int i = 0; i < 4; ++i){
//            System.out.print(check[i]+ ":");
//        }

        return hash;
    }
}
