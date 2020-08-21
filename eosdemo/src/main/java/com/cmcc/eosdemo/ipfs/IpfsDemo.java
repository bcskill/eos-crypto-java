package com.cmcc.eosdemo.ipfs;

import com.cmcc.eos.crypto.ec.EosPrivateKey;
import com.cmcc.eos.crypto.ec.EosPublicKey;
import com.cmcc.eos.crypto.util.Base58;
import com.cmcc.eos.crypto.util.CryptUtil;
import com.cmcc.eos.crypto.util.MTRandom;
import com.cmcc.eos.crypto.util.StandardCharsets;
import com.google.gson.JsonObject;
import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.multiaddr.MultiAddress;
import io.ipfs.multihash.Multihash;
import org.bouncycastle.crypto.InvalidCipherTextException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;


/**
 * 基于 ECC+AES 双向验证 方式，加密数据，上传到IPFS中代码示例。
 */
public class IpfsDemo {


    private static IPFS ipfs = new IPFS(new MultiAddress("/ip4/117.134.46.50/tcp/3925"));


    public static void main(String[] args) throws IOException {
        uploadIpfs();
    }

    /**
     *  将数据进行 ECC+AES 加密后，传输到ipfs中
     * @throws UnsupportedEncodingException
     */
    public static void uploadIpfs() throws IOException {


        /**
         *
         * sender    发送方公私钥对
         *
         * EOS8g1u3ktAGHs4QsVp9aeaWNebFLtprQHwpaSjegx6iEuoTNhjXU
         * 5KTZYCDdcfNrmEpcf97SJBCtToZjYHjHm8tqTWvzUbsUJgkxcfk
         *
         * receiver  接收方公私钥对
         *
         * EOS7ez2gagfoXw9XdW3kRx3EsCoWvupGR6u6ZJhFPEe9Q12V8JgUL
         * 5JUrqxYcssR9LLVtWDeQcc9HCX4FEqBG7d9GW6t7mvmB1rUuZr9
         *
         * receiver  接收方公私钥对
         * EOS5WMHqw6jDDBPBm7JXTHemAwtSo2tp93pRysJMRhiT1zUYb24vL
         * 5HrcVeuHHNwHsivrMoJ9XvU6EM7Q2wQ2ECiy8GeoiuamhNiSuZq
         */

        // 1.  调用钱包获取 发送方私钥
        String senderPrivateKey =  "5KTZYCDdcfNrmEpcf97SJBCtToZjYHjHm8tqTWvzUbsUJgkxcfk";
        EosPrivateKey senderECPrivateKey = new EosPrivateKey(senderPrivateKey);
//        EosPublicKey senderECPublicKey = new EosPublicKey(senderPublicKey);
        // 2.  根据私钥 生成公钥。 或者直接根据公钥 调用钱包获取私钥。 都可以，看具体业务需求
        EosPublicKey senderECPublicKey = senderECPrivateKey.getPublicKey();

        String senderPublicKey = senderECPublicKey.toString();
        /**
         * 调用钱包获取 接收方私钥   获取公私钥方式 根据业务需求确定。
         *  1. 可以根据公钥，从钱包里获取私钥
         *  2. 也可以直接从钱包里取出私钥，反向生成公钥
         */
        String receiverPrivateKey = "5JUrqxYcssR9LLVtWDeQcc9HCX4FEqBG7d9GW6t7mvmB1rUuZr9";
        EosPrivateKey receiverECPrivateKey = new EosPrivateKey(receiverPrivateKey);
        EosPublicKey receiverECPublicKey = receiverECPrivateKey.getPublicKey();
//        String receiverPublicKey =  "EOS7ez2gagfoXw9XdW3kRx3EsCoWvupGR6u6ZJhFPEe9Q12V8JgUL";
        String receiverPublicKey = receiverECPublicKey.toString();

        /**
         * 使用 发送者方私钥 和接收方公钥，生成 aes key, 对数据进行加密
         * nonce  为初始化向量，可以使用固定值，
         *                      也可以使用随机值，并使用私有协议。根据业务需求选择。
         *  请参考技术规范中约定的格式
         */
        byte[] nonce = new byte[16];
        MTRandom random=new MTRandom();
        random.nextBytes(nonce);

        // 待加密 数据
        byte[] params = "{\"age\": 1,\"汉字\":\"汉字测试。为初始化向量，可以使用固定值，\"，\"12345\":\"24qqwazzxdtttdxkaskjewuizckczxnlsdosasda4!!!@#$$%^&&*(()(^#\"}".getBytes("utf8");

        System.out.println("原始加密数据： " + new String(params,StandardCharsets.UTF_8));

        byte[] encrypted = new byte[0];
        try {
            encrypted = CryptUtil.encrypt(senderECPrivateKey,receiverECPublicKey,nonce,params);
        } catch (InvalidCipherTextException e) {
            e.printStackTrace();
            System.out.println("  do something!!!!");
        }

        System.out.println("加密后数据： " + Base58.encode(encrypted));

        JsonObject object = new JsonObject();
        object.addProperty("Data", Base58.encode(encrypted));

        List<byte[]> objectData = new ArrayList<>(1);

        objectData.add(object.toString().getBytes(StandardCharsets.UTF_8));

        List<MerkleNode> merkleNodes =  ipfs.object.put(objectData);

        MerkleNode node = merkleNodes.get(0);

        /**
         * 可以将该值，上传到区块链网络中。
         */
        String nodeHash = node.hash.toBase58();

        System.out.println( " 上传 ipfs 后，获取到的 hash值 "+ nodeHash);

        System.out.println(" 根据 上传获取到的 ipfs hash，从 ipfs 下载 数据。");

        Multihash multihash = Multihash.fromBase58(nodeHash);

        MerkleNode merkleNode = ipfs.object.get(multihash);

        byte[] ipfsData = merkleNode.data.get();

        System.out.println("从 ipfs 获得到的数据 "+new String(ipfsData));

        byte[] decode = Base58.decode(new String(ipfsData));

        try {
            byte[] plainText = CryptUtil.decrypt(receiverECPrivateKey,senderECPublicKey,nonce,decode);
            // 解密后数据
            System.out.println("解密后数据 :  "+new String(plainText, "utf8"));
        } catch (InvalidCipherTextException e) {
            e.printStackTrace();
            System.out.println("  do something!!!!");
        }
    }
}