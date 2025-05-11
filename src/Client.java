import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Scanner;

public class Client {
    //Global variables
    public static final String FILES_PATH = "./src/files"; //We will limit the files we send to those in the "files" folder

    //We have this as a global variable as to help the client determine which file to send (and to not instantiate twice between methods)
    File[] fileList = new File(FILES_PATH).listFiles(); //An array of all files within the "files folder"

    private Socket clientSock;
    private DataInputStream receiveData;
    private DataOutputStream sendData;

    //These are globalized for the same reason as fileList
    private Scanner write = new Scanner(System.in);
    private StringBuilder message = new StringBuilder();
    private String username;
    private String choice;

    public Client() {
        try{
            clientSock = new Socket("127.0.0.1",Server.PORT);
            receiveData = new DataInputStream(new BufferedInputStream(clientSock.getInputStream()));
            sendData = new DataOutputStream(clientSock.getOutputStream());

            //Send client username when connection made
            System.out.println("Enter your username: ");
            username = write.nextLine();
            sendData.writeUTF(username);

            new Thread(this::listen).start(); //Listens for server messages as they come in (How we receive messages)
            createData(); //This method contains its own break condition, no while loop in this method

            //Since the close function is handled by the handler, close() isn't needed here, otherwise, "null disconnected" appears.

        } catch(IOException e){
            e.printStackTrace();
        }
    }

    //Listen for a message from the server
    private void listen(){
        try{
            while(true){
                String header = receiveData.readUTF(); //Look for the header of the server message

                //To guarantee our code can work with files other than .txt, since we are required to read the bytes rather can tokens
                if(header.contains("FILE|")){
                    String[] parts = header.split("\\|",4);
                    String recipient = parts[1];
                    String filename = parts[2];
                    String sender = parts[3];

                    //Read file length
                    int fileLength = receiveData.readInt();
                    byte[] fileBytes = new byte[fileLength];
                    receiveData.readFully(fileBytes);

                    //Save file in FILES_PATH folder
                    File outFile = new File(FILES_PATH + "/received" + filename);
                    try(FileOutputStream fos = new FileOutputStream(outFile)){
                        System.out.println(header);
                        fos.write(fileBytes);
                    }
                }
                else System.out.print(header);
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    //Protocol for message creation
    private void createData() throws IOException{
        while(true){
            System.out.println("What would you like to send\n(1) A message\n(2) A file");

            int choice = 0;
            while (true) {
                if (write.hasNextInt()) {
                    choice = write.nextInt();
                    if (choice > 0 && choice <= 2) break;
                    else System.out.println("Please enter 1 or 2");
                } else System.out.println("Invalid Input, please enter 1 or 2");
                write.next();
            }

            // The value of choice determines the protocol used to creating the message to server
            if (choice == 1) clientMessage();
            else clientFileSend();

            sendData.flush();
            System.out.println("Would you like to exit (Y/N)");

            if(write.next().equalsIgnoreCase("Y")) break;
        }

    }



    //Creates messages that the clients sends to the handler to be processed to the server
    private void clientMessage() throws IOException{
        //For subsequent message to prevent the entire message appending to previous
        message = new StringBuilder();
        //We want to use this String as the data we send to the server
        message.append("MSG|");
        System.out.println("Would you like to send to everyone? (Y/N)");

        while(true){
            choice = write.next();
            if(choice.equalsIgnoreCase("Y") || choice.equalsIgnoreCase("N")) break;
            System.err.print("Invalid input, please enter \"Y\" or \"N\"");
        }
        write.nextLine(); //Clear buffer
        if(choice.equalsIgnoreCase("Y")) {
            message.append("ALL|");
        }
        else{
            System.out.println("Enter recipient name:");
            message.append(write.nextLine()).append("|");
        }

        System.out.println("Enter your message:");
        message.append(write.nextLine()).append("|").append(username);

        sendData.writeUTF(String.valueOf(message));
        System.out.println("Message sent.");

    }

    //Creates message for file sending to the handler to process out to the server
    private void clientFileSend() throws IOException{
        //For subsequent message to prevent the entire message appending to previous
        message = new StringBuilder();
        message.append("FILE|");
        write.nextLine(); //Clear buffer
        System.out.print(Menu() + "\nPick a file to send (Type the ID number) ");

        int fileIndex = Integer.parseInt(write.nextLine()) - 1;
        File selectedFile = fileList[fileIndex];

        System.out.println("Would you like to send to everyone? (Y/N)");

        while(true){
            choice = write.next();
            if(choice.equalsIgnoreCase("Y") || choice.equalsIgnoreCase("N")) break;
            System.err.print("Invalid input, please enter \"Y\" or \"N\"");
        }

        //If method goes wrong, check this line
        write.nextLine(); //Clear buffer

        if(choice.equalsIgnoreCase("Y")) {
            message.append("ALL|");
        }
        else{
            System.out.println("Enter recipient name:");
            message.append(write.nextLine()).append("|");
        }
        message.append(selectedFile.getName()).append("|").append(username);

        sendData.writeUTF(String.valueOf(message));

        //Send file bytes and length to the server
        byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
        sendData.writeInt(fileBytes.length);
        sendData.write(fileBytes);

        sendData.flush();

        System.out.println("File Sent.");
    }

    //Creates a display of all available files in the "files folder"
    private String Menu() throws IOException{
        StringBuilder menu = new StringBuilder("--Files--\n"); //Just a little header

        for(int i = 0; i< fileList.length;i++)
            menu.append(String.format("* %d: %s\n", i + 1, fileList[i].getName())); //Prints the files name along with an ID number (index + 1)
        return String.valueOf(menu);
    }


    public static void main(String[] args){
        new Client();
    }
}