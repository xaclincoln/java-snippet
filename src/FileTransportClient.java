import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Administrator on 2017/4/8.
 */
public class FileTransportClient {
    private String _ip;
    private int _port = 9000;
    private Socket _sock;

    FileTransportClient(String ip, int port) throws IOException {
        this._ip = ip;
        this._port = port;
        _sock = new Socket();

    }

    void Connect() throws IOException {
        _sock.connect(new InetSocketAddress(_ip, _port));
    }

    void SendFile(String filePath) throws IOException, NoSuchAlgorithmException {
        OutputStream writer = _sock.getOutputStream();
        InputStream reader = _sock.getInputStream();
        FileTransportConfig config = FrameUtil.generateTransportConfig(filePath);
        byte[] frame = FrameUtil.makeFileTransportConfigFrame(config);

        byte[] response = repeatSendingXmlConfigFrameIfValidationFailed(writer, reader, frame);
        if (response[0] == FrameUtil.FrameTypeXmlTransportConfigResponse) {
            long startPosition = FrameUtil.byteArrayToLong(response, 5);
            int startChunkOrdinalNum = determineChunkBelonging(startPosition);
            for (int i = startChunkOrdinalNum; i < config.chunks.size(); ++i) {
                long endPosition = startPosition + FrameUtil.ChunkSize - 1;
                repeatSendingChunkIfValidationFailed(writer, reader, filePath, startPosition, endPosition);
                startPosition = endPosition + 1;
            }
        } else {

        }

        writer.close();
        reader.close();
        _sock.close();
    }

    void repeatSendingChunkIfValidationFailed(OutputStream writer,
                                              InputStream reader,
                                              String filePath,
                                              long startPosition,
                                              long endPositionInclusive) throws IOException {
        do {
            sendChunk(writer, filePath, startPosition, endPositionInclusive);
            byte[] requestValidationFrame = FrameUtil.makeValidationRequestFrame();
            writer.write(requestValidationFrame, 0, requestValidationFrame.length);
            byte[] response = getResponse(reader);
            if (response[0] == FrameUtil.FrameTypeChunkValidationResponse) {
                //验证帧的第5个字节==0时表示成功
                System.out.println("chunk validation passed");
                if (response[5] == 0) {
                    return;
                }
            } else {

            }
        } while (true);
    }

    void sendChunk(OutputStream writer, String filePath, long startPosition, long endPositionInclusive) throws IOException {
        byte[] frame = FrameUtil.makeDataFrame();
        InputStream reader = Files.newInputStream(Paths.get(filePath));
        reader.skip(startPosition);

        int totalCount = 0;
        while (true) {
            long toRead = (endPositionInclusive - startPosition + 1) - totalCount;
            if (toRead > FrameUtil.DataLength) {
                toRead = FrameUtil.DataLength;
            }
            int count = reader.read(frame, FrameUtil.HeaderLength, (int) toRead);
            setDataFrameBodyLength(frame, count);
            writer.write(frame, 0, count + FrameUtil.HeaderLength);
            totalCount += count;
            if (count < toRead || totalCount == endPositionInclusive - startPosition + 1) {
                break;
            }
        }
        reader.close();
    }

    void setDataFrameBodyLength(byte[] frame, int length) {
        byte[] lengthBytes = ByteBuffer.allocate(4).putInt(length).array();
        System.arraycopy(lengthBytes, 0, frame, 1, lengthBytes.length);
    }

    int determineChunkBelonging(long startPosition) {
        return (int) (startPosition / FrameUtil.ChunkSize);
    }


    byte[] repeatSendingXmlConfigFrameIfValidationFailed(OutputStream writer, InputStream reader, byte[] frame) throws IOException {
        while (true) {
            writer.write(frame, 0, frame.length);
            byte[] response = getResponse(reader);
            if (response[0] == FrameUtil.FrameTypeRetransmit) {
                continue;
            } else {
                return response;
            }
        }
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

    public static void main(String[] args) {
        try {
            FileTransportClient client = new FileTransportClient("127.0.0.1", 9000);
            client.Connect();
            client.SendFile("D:\\Downloads\\Git-2.12.2.2-64-bit.exe");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }


        System.out.println("Hello World!");
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
