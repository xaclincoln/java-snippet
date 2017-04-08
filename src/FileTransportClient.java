import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
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

    void SendFileTransportXmlConfig(String filePath) throws IOException, NoSuchAlgorithmException {
        OutputStream writer = _sock.getOutputStream();
        byte[] frame = FrameUtil.makeFileTransportConfigFrame(filePath);
        writer.write(frame, 0, frame.length);
    }

}
