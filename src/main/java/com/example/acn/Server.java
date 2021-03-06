package com.example.acn;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    ServerSocket serverSocket;

    Server() {
        try {
            serverSocket = new ServerSocket(30000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        Server server = new Server();
        server.startServer();
    }


    private void startServer() {
        try {
            int count = 1;
            while (true) {
                Socket socket = serverSocket.accept();
                ServerThread serverThread = new ServerThread(socket, count);
                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ServerThread extends Thread {

    DataOutputStream writer;
    DataInputStream reader;
    ObjectInputStream readerObj;
    ObjectOutputStream writerObj;
    Directory rootDirectory;


    Socket socket;
    int clientId;

    ServerThread(Socket socket, int count) {
        this.socket = socket;
        this.clientId = count;
        start();
    }

    @Override
    public void run() {

        try {
            writerObj = new ObjectOutputStream(socket.getOutputStream());
            readerObj = new ObjectInputStream(socket.getInputStream());
            writer = new DataOutputStream(socket.getOutputStream());
            reader = new DataInputStream(socket.getInputStream());

            int counter = 1;
            while (true) {
                System.out.println("File received request count : " + counter);
                int commandSize = (int) reader.readLong();
                byte[] inputBuffer = new byte[commandSize];
                reader.read(inputBuffer, 0, commandSize);
                String msg = new String(inputBuffer).trim();
                System.out.println("Received command " + msg);
                String[] commandAndFile = msg.split("\\s+");
                if (commandAndFile.length != 2) {
                    System.out.println("Invalid Input! Invalid input from client! ");
                    return;
                }
                File file = new File(commandAndFile[1]);

                if (commandAndFile[0].equals("GETF")) {
                    if (getStatus(file)) {
                        sendFile(file, socket, writer);
                    }
                } else if (commandAndFile[0].equals("PUTDIRECTORY")) {
                    getfDirectory(file, socket, reader);
                } else if (commandAndFile[0].equals("GETDIRECTORY")) {
                    putfDirectory(file, socket, writer);
                } else if (commandAndFile[0].equals("PUTF")) {
                    receiveFile(file, socket, reader);
                    System.out.println("File uploaded successfully!");
                } else if (commandAndFile[0].equals("SYNC")) {
                    if (getSyncStatus(file)) {
                            sendDirectoryStructure(commandAndFile[1]);
                    }
                } else if (commandAndFile[0].equals("QUIT")) {
                    System.exit(0);
                } else {
                    System.out.println("Invalid Input! Invalid input from client! ");
                }
                System.out.println("Command done!");
                counter++;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean getSyncStatus(File file) {
        try {

            if (file.exists() && file.isDirectory()) {
                String msg = "Successful!";
                System.out.println(msg);
                long length = msg.length();

                writer.writeLong((int) length);

                writer.write(msg.getBytes(), 0, msg.length());
            } else {

                String msg = "Error in input file!";
                System.out.println(msg);
                long length = msg.length();

                writer.writeLong((int) length);

                writer.write(msg.getBytes(), 0, msg.length());
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean getStatus(File file) {
        try {

            if (!file.exists()) {
                String msg = "Error in input file!";
                System.out.println(msg);
                long length = msg.length();

                writer.writeLong((int) length);

                writer.write(msg.getBytes(), 0, msg.length());
                return false;
            } else {
                String msg = "Successful!";
                System.out.println(msg);
                long length = msg.length();

                writer.writeLong((int) length);

                writer.write(msg.getBytes(), 0, msg.length());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private void sendDirectoryStructure(String dirName) {
        File file = new File(dirName);
        Directory rootDirectory = new Directory(dirName);

        rootDirectory.populateDirectory(file.listFiles());
        System.out.println(rootDirectory);

        sendObject(rootDirectory);
    }

    private void sendObject(Directory rootDirectory) {
        try {
            writerObj.writeObject(rootDirectory);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void putfDirectory(File file, Socket sock, DataOutputStream reader) {
        Directory dir = new Directory(file.getName());
        dir.populateDirectory(file.listFiles());
        sendObject(dir);
        String path = file.getPath();
        putf(dir, sock, reader, path);

    }

    private void putf(Directory dir, Socket sock, DataOutputStream reader, String path) {
        try {

            System.out.println(path);
            for (Directory directory : dir.directoryList) {
                File inputFile = new File(directory.getDirectoryName());
                if (inputFile.isDirectory()) {
                    putf(directory, sock, reader, inputFile.getPath());
                } else {
                    sendFile(inputFile, sock, reader);
                }
            }
            for (FileDetails fileDetails : dir.fileDetailsList) {
                File inputFile = new File(path + "/" + fileDetails.fileName);
                sendFile(inputFile, sock, reader);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Directory receiveObject() {
        Directory dirObj = null;
        try {
            dirObj = (Directory) readerObj.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return dirObj;

    }

    private void getfDirectory(File file, Socket sock, DataInputStream reader) {
        Directory directory = receiveObject();
        System.out.println(file.getPath());
        getf(directory, sock, reader, file.getPath());

    }

    private void getf(Directory dir, Socket sock, DataInputStream reader, String path) {
        try {
            System.out.println(path);
            System.out.println(dir.getDirectoryName());
            File file = new File(path);
            if (!file.exists()) {
                file.mkdir();
            }
            System.out.println(dir.getDirectoryName());
            for (Directory directory : dir.directoryList) {
                getf(directory, sock, reader, path);
            }
            for (FileDetails fileDetails : dir.fileDetailsList) {
                File inputFile = new File(path + "/" + fileDetails.fileName);
                receiveFile(inputFile, sock, reader);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendFile(File dir, Socket sock, DataOutputStream oos) throws Exception {
        byte[] buff = new byte[sock.getSendBufferSize()];
        int bytesRead = 0;

        InputStream fileReader = new FileInputStream(dir);
        long length = dir.length();

        oos.writeLong((int) length);

        while ((bytesRead = fileReader.read(buff)) > 0) {
            oos.write(buff, 0, bytesRead);
        }
        oos.flush();

        fileReader.close();
    }

    private static void receiveFile(File dir, Socket sock, DataInputStream ois) throws Exception {
        System.out.println(dir.getPath());
        if (!dir.exists()) {
            dir.getParentFile().mkdirs();
            dir.createNewFile();
        }

        FileOutputStream wr = new FileOutputStream(dir);
        byte[] outBuffer = new byte[sock.getReceiveBufferSize()];
        int bytesReceived = 0;
        long fileSize = ois.readLong();
        while (fileSize > 0 && (bytesReceived = ois.read(outBuffer, 0, (int) Math.min(outBuffer.length, fileSize))) != -1) {
            wr.write(outBuffer, 0, bytesReceived);
            fileSize -= bytesReceived;
        }
        wr.flush();
        wr.close();

    }


}
