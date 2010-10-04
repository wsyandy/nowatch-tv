package net.nowatch.service;


interface IMusicService {

    boolean isPlaying();
    void openFile(String msg, String file, String type, int item_id);
    void openFileId(int id);
    void seek(int position);
    void play();
    void pause();
    long getPosition();
    int getBufferPercent();
    
}