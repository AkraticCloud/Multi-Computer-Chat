import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    public static final int PORT = 3030;
    private ServerSocket server;
    private Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private int clientCount = 0;

    public Server() {
        try {
            this.server = new ServerSocket(3030);
            System.out.println("Server started on port " + PORT);

            while(true) {
                acceptClients();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptClients() throws IOException {
        Socket clientSock =server.accept();
        clientCount++;
        DataInputStream in = new DataInputStream(clientSock.getInputStream());
        String username = in.readUTF();

        ClientHandler handler = new ClientHandler(clientSock, username);
        clients.add(handler);
        new Thread(handler).start();
        System.out.println(username + " connected.");
    }

    public void broadcastMessage(String user, String message) {
        System.out.printf("Broadcasting message from %s\n", user);
        for(ClientHandler recipients : clients) {
            if(!(recipients.getUsername().equals(user)))
                recipients.sendText("MSG|ALL|" + user + "|" + message);
        }
    }

    public void sendMessageTo(String user, String recipient, String message) {
        //Check if the recipient exists
        if(recipientDoesNotExist(recipient)){
            System.out.printf("%s failed to send message: Recipient does not exist", user); //Server notification
            for(ClientHandler client : clients)
                if(client.getUsername().equals(user))
                    client.sendText("ERROR|User " + recipient + "does not exist."); //Inform the sender
            return;
        }

        System.out.printf("Sending message from %s to %s\n", user, recipient);
        for(ClientHandler client : clients) {
            if (client.getUsername().equals(recipient)) {
                client.sendText("MSG|" + user + "|" + recipient + "|" + message);
                break;
            }
        }

    }

    public void broadcastFile(String user, String filename, byte[] content) {
        System.out.printf("Broadcasting file from %s", user);
        for(ClientHandler sender : clients) {
            sender.sendFile("FILE|ALL|" + filename + "|" + user, content);
        }

    }

    public void sendFileTo(String user, String recipient, String filename, byte[] content) {
        //Validate recipient
        if(recipientDoesNotExist(recipient)){
            System.out.printf("%s failed to send message: Recipient does not exist\n", user); //Server notification
            for(ClientHandler client : clients)
                if(client.getUsername().equals(user))
                    client.sendText("ERROR|User " + recipient + "does not exist.");
            return;
        }

        System.out.printf("Sending file from %s to %s\n", user, recipient);
        for(ClientHandler client : clients) {
            if (client.getUsername().equals(recipient)) {
                client.sendFile("FILE|" + recipient + "|" + filename + "|" + user, content);
                break;
            }
        }

    }

    public boolean recipientDoesNotExist(String name){
        for (ClientHandler client: clients)
            if(!client.getUsername().equals(name)) return true;
        return false;
    }


    public static void main(String[] var0) {
        new Server();
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String username;


        public ClientHandler(Socket sock, String name) {
            socket = sock;
            username = name;

            try {
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        public String getUsername() {
            return username;
        }

        public void run() {
            try {
                while(true) {
                    String clientMessage = in.readUTF();

                    String[] headers = clientMessage.split("\\|", 4);
                    String type = headers[0];
                    String recipient = headers[1];
                    String content = headers[2];
                    String sender = headers[3];

                    if (type.equals("MSG")) {
                        if (recipient.equals("ALL")) {
                            broadcastMessage(sender, content);
                        } else {
                            sendMessageTo(sender, recipient, content);
                        }
                    } else if ("FILE".equals(type)) {
                        int fileLength = this.in.readInt();
                        byte[] fileBytes = new byte[fileLength];
                        in.readFully(fileBytes);

                        if ("ALL".equalsIgnoreCase(recipient)) {
                            broadcastFile(sender, content, fileBytes);
                        } else {
                            sendFileTo(sender, recipient, content, fileBytes);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println(username + " disconnected.");
            } finally {
                try {
                    socket.close();
                } catch (IOException var15) {
                }
                clients.remove(this);
            }

        }


        void sendText(String message) {
            try {
                out.writeUTF(message);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        void sendFile(String header, byte[] data) {
            try {
                out.writeUTF(header);
                out.writeInt(data.length);
                out.write(data);
                out.flush();
            } catch (IOException recipients) {
                recipients.printStackTrace();
            }

        }
    }
}
