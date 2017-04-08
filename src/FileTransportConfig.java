import java.util.ArrayList;

public  class  FileTransportConfig {
    public String fileName;
    public long fileSize;
    public String md5Hash;
    public ArrayList<Chunk> chunks;

    FileTransportConfig(){
        chunks = new ArrayList<>();
    }
}

class Chunk {
    public int ordinalNum;
    public String md5Hash;

    Chunk(int num,String hash){
        this.ordinalNum = num;
        this.md5Hash = hash;
    }
}
