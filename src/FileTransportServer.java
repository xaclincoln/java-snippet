import org.dom4j.DocumentException;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

/**
 * Created by Administrator on 2017/4/8.
 */
public class FileTransportServer {
    private String _ip;
    private int _port;
    private ServerSocket _sock;
    private final String receivePath = "./接收/";


    FileTransportServer(String ip, int port) {
        _ip = ip;
        _port = port;

    }

    void start() {

        try {
            _sock = new ServerSocket();
            _sock.bind(new InetSocketAddress(_ip, _port));
        } catch (IOException e) {
            return;
        }

        while (true) {
            try {
                Socket client = _sock.accept();
                InputStream reader = client.getInputStream();
                OutputStream writer = client.getOutputStream();

                //首先接收文件传输的XML配置文件
                byte[] response = getResponse(reader);
                boolean valid = validate(response);
                if (valid && response[0] == FrameUtil.FrameTypeXmlTransportConfig) {
                    int xmlConfigLength = FrameUtil.byteArrayToInt(response, 5);
                    String xmlString = new String(response, FrameUtil.HeaderLength, xmlConfigLength, "UTF-8");
                    FileTransportConfig config = FrameUtil.ParseFileTransportConfigXml(xmlString);
                    //saveFileTransportConfig(xmlString, config.fileName);
                    //应答客户端文件续传的位置
                    int resumedChunkNum = getResumedTransferChunkNum(config.fileName);
                    byte[] responseFrame = FrameUtil.makeFileTransportConfigResponseFrame(resumedChunkNum * FrameUtil.ChunkSize);
                    writer.write(responseFrame);
                    createReceiveFileIfNotExisted(config.fileName);
                    receiveChunks(reader, writer, config, resumedChunkNum);
                } else {
                    writer.write(FrameUtil.makeRetransmitFrame());
                }
            } catch (IOException ex) {

            } catch (NoSuchAlgorithmException ex) {
            } catch (DocumentException ex) {
            }

        }

    }

    void receiveChunks(InputStream reader, OutputStream writer, FileTransportConfig config, int startChunk)
            throws IOException, NoSuchAlgorithmException {
        for (int i = startChunk; i < config.chunks.size(); i++) {
            boolean result = repeatReceiveChunkIfValidationFailed(reader, writer, config.fileName, config.chunks.get(i));
            //如果成功接收块，把块追加上主文件
            //如果出现任何不满足协议的帧，则断开和客户端的连接
            if (!result) {
                appendChunkToMainFile(config.fileName, i);
            } else {
                reader.close();
                writer.close();
                break;
            }
        }
    }

    void appendChunkToMainFile(String mainFileName, int chunkOrdinalNum) throws IOException {
        try {
            DataOutputStream out = new DataOutputStream(new FileOutputStream(receivePath + mainFileName, true));
            InputStream in = new FileInputStream(getChunkFilePath(mainFileName, chunkOrdinalNum));
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    void createReceiveFileIfNotExisted(String fileName) throws IOException {
        File target = new File(receivePath + fileName);
        if (!target.exists()) {
            target.createNewFile();
        }
    }

    String getChunkFilePath(String fileName, int chunkOrdinalNum) {
        return receivePath + fileName + ".chunk" + String.format("%04d", chunkOrdinalNum);
    }

    boolean repeatReceiveChunkIfValidationFailed(InputStream reader, OutputStream writer, String fileName, Chunk c)
            throws IOException, NoSuchAlgorithmException {
        while (true) {
            //如果出现Md5验证不通过，则重新传
            File chunk = new File(getChunkFilePath(fileName, c.ordinalNum));
            DataOutputStream fileWriter = new DataOutputStream(new FileOutputStream(chunk, false));
            ChunkReceiveState state = receiveChunk(reader, writer, fileWriter, c);
            if (state == ChunkReceiveState.Succeed) {
                return true;
            } else if (state == ChunkReceiveState.IllegalFrame) {
                return false;
            } else if(state == ChunkReceiveState.ClientDisconnected){
                return false;
            }else {
                //如果验证不通过，则重新接收
                continue;
            }
        }
    }

    enum ChunkReceiveState {
        Succeed,
        NeedRetransmit,
        IllegalFrame,
        ClientDisconnected,
    }

    ChunkReceiveState receiveChunk(InputStream reader, OutputStream writer, OutputStream fileWriter, Chunk c)
            throws IOException, NoSuchAlgorithmException {
        byte[] bytes = new byte[FrameUtil.DataLength + FrameUtil.HeaderLength];
        MessageDigest md5 = MessageDigest.getInstance("md5");
        while (true) {
            int count = reader.read(bytes, 0, bytes.length);
            if(count <= 0){
                return ChunkReceiveState.ClientDisconnected;
            }
            if (bytes[0] == FrameUtil.FrameTypeData) {
                md5.update(bytes, FrameUtil.HeaderLength, count);
                fileWriter.write(bytes, FrameUtil.HeaderLength, count);
            } else if (bytes[0] == FrameUtil.FrameTypeChunkValidationRequest) {
                byte[] hash = md5.digest();
                boolean needRetransmit = !DatatypeConverter.printHexBinary(hash).equalsIgnoreCase(c.md5Hash);
                writer.write(FrameUtil.makeChunkValidationResponseFrame(needRetransmit));
                return needRetransmit ? ChunkReceiveState.NeedRetransmit : ChunkReceiveState.Succeed;
            } else {
                return ChunkReceiveState.IllegalFrame;
            }
        }
    }

    class ResumedTransferState {

    }

    int getResumedTransferChunkNum(String fileName) throws IOException {
        File targetFile = new File(receivePath + fileName);
        if (!targetFile.exists()) {
            return 0;
        }
        long size = targetFile.length();
        //块的命名规则为 "{fileName}.chunk{序号}"
        //查找文件同名的最后一个块，获取其大小
        File recvDir = new File(receivePath);
        File[] chunks = recvDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(fileName + ".chunk");
            }
        });

        if (chunks.length == 1) {
            return 0;
        }

        //重传时，如果某个块没有传输完全，则从头重传此块
        List chunkList = Arrays.asList(chunks);
        File lastChunk = Collections.max(chunkList, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        String lastChunkFileName = lastChunk.getName();
        return Integer.parseInt(lastChunkFileName.substring(lastChunkFileName.length() - 4, lastChunkFileName.length()));

    }

    boolean validate(byte[] frame) throws NoSuchAlgorithmException {
        int xmlConfigLength = FrameUtil.byteArrayToInt(frame, 5);
        MessageDigest md5 = MessageDigest.getInstance("md5");
        md5.update(frame, 0, xmlConfigLength + FrameUtil.HeaderLength);
        byte[] md5Hash = md5.digest();
        return md5.isEqual(md5Hash, Arrays.copyOfRange(frame, xmlConfigLength + FrameUtil.HeaderLength, frame.length));
    }

    byte[] getResponse(InputStream reader) throws IOException {
        byte[] header = new byte[FrameUtil.HeaderLength];
        reader.read(header, 0, header.length);
        int bodyLength = FrameUtil.byteArrayToInt(header, 1);
        if (bodyLength != 0) {
            byte[] result = new byte[header.length + bodyLength];
            reader.read(result, header.length, bodyLength);
            System.arraycopy(header, 0, result, 0, header.length);
            return result;
        } else {
            return header;
        }
    }

    void saveFileTransportConfig(String config, String fileName) throws IOException {
        File dir = new File(receivePath);
        if (!dir.exists()) {
            dir.mkdir();
        }
        FileWriter writer = new FileWriter(receivePath + fileName);
        writer.append(config);
        writer.close();
    }


    public static void main(String[] args) {

        FileTransportServer server = new FileTransportServer("127.0.0.1", 9000);
        server.start();


        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
