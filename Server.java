import java.io.*;
import java.util.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
 
// Server class
public class Server
{

    //Vector to store all clients
    static Vector<ClientHandler> totalClients = new Vector<>();

    // Vector to store active clients
    static Vector<ClientHandler> activeClients = new Vector<>();
     
    // counter for clients
    static int i = 0;
 
    public static void main(String[] args) throws IOException
    {
        // server is listening on port 1234
        ServerSocket ss = new ServerSocket(1234);
         
        Socket s;

        System.out.println("Server is listening on port 1234");
         
        // running infinite loop for getting client request
        while (true)
        {
            // Accept the incoming request
            s = ss.accept();
 
            System.out.println("New client request received : " + s);
             
            // obtain input and output streams
            DataInputStream dis = new DataInputStream(s.getInputStream());
            DataOutputStream dos = new DataOutputStream(s.getOutputStream());
             
            System.out.println("Creating a new handler for this client...");
 
            // Create a new handler object for handling this request.
            ClientHandler mtch = new ClientHandler(s,"client " + i, dis, dos);
 
            // Create a new Thread with this object.
            Thread t = new Thread(mtch);

            System.out.println("Adding this client to active and total client lists");
 
            // add this client to active clients list
            activeClients.add(mtch);
            totalClients.add(mtch);
 
            // start the thread.
            t.start();
 
            // increment i for new client.
            // i is the total number of entries any number of clients have made into the program
            i++;
            
            System.out.println("Total clients currently present: " + totalClients.size());
            System.out.println("Active clients currently present: " + activeClients.size());
        }
    }

    // Get the list of active clients
    public static String getActiveClientList() {
        StringBuilder clientList = new StringBuilder();
        for (ClientHandler client : activeClients) {
            clientList.append(client.toName()).append("\n");
        }
        return clientList.toString();
    }

    // Get the list of all clients on the server    
    public static String getTotalClientList(){
        StringBuilder totalClientList = new StringBuilder();
        for(ClientHandler client : totalClients) {
            totalClientList.append(client.toString()).append("\n");
        }
        return totalClientList.toString();
    }
}
 
// ClientHandler class
class ClientHandler implements Runnable
{
    Scanner scn = new Scanner(System.in);
    private String name;
    final DataInputStream dis;
    final DataOutputStream dos;
    Socket s;
    boolean isloggedin;
    boolean isnameset;
     
    // constructor
    public ClientHandler(Socket s, String name,
                            DataInputStream dis, DataOutputStream dos) {
        this.dis = dis;
        this.dos = dos;
        this.name = name;
        this.s = s;
        this.isloggedin=true;
        this.isnameset=false;
    }
 
    @Override
    public void run() {

        String received = "";
        while (true)
        {
            try
            {
                if(!this.isnameset){
                    dos.writeUTF("Please enter your name:");
                    String clientName = dis.readUTF();
                    this.name = clientName;
                    System.out.println("Client connected: " + clientName);
                    this.isnameset = true; // Set the flag to indicate the name is set
                    //this will prevent the client from having to enter their name for every action they carry out.
                }
                // receive the string
                received = dis.readUTF();
                 
                System.out.println(received);
                
                //login-logout functionality
                if (received.equals("logout")) {
                    boolean removeFromActiveList = false;
                    if (Server.activeClients.contains(this)) {
                        Server.activeClients.remove(this); // Remove from active client list
                        this.isloggedin = false;
                        dos.writeUTF("You have been logged out. Do you want to exit the server? (Y/N)");
                        String logoutResponse = dis.readUTF();
                        if (logoutResponse.equalsIgnoreCase("N")) {
                            removeFromActiveList = true;
                        } else if (logoutResponse.equalsIgnoreCase("Y")) {
                            Server.totalClients.remove(this); // Remove from total client list
                            break;
                        }
                    }
                    if (removeFromActiveList) {
                        while (true){
                            dos.writeUTF("Do you want to log back in? (Y/N)");
                            String loginResponse = dis.readUTF();
                            if (loginResponse.equalsIgnoreCase("Y")) {
                                Server.activeClients.add(this); // Add back to active client list
                                break;
                            }
                        }
                    }
                }
            
                //if client requests the list of available clients
                if(received.equals("getactiveclients")){
                    String activeClientList = Server.getActiveClientList();
                    dos.writeUTF("Active Clients:\n" + activeClientList);
                }

                //if client requests the list of all clients on the server
                if(received.equals("getallclients")){
                    String allClientList = Server.getTotalClientList();
                    dos.writeUTF("All Clients:\n" + allClientList);
                }

                //Message scheduler
                if (received.equals("schedule")) {
                    dos.writeUTF("Enter the message#recipient to be scheduled:");
                    String scheduledText = dis.readUTF();

                    //break the string into message and recipient part
                    StringTokenizer st = new StringTokenizer(scheduledText, "#");
                    String scheduledMsgToSend = st.nextToken();
                    String recipient = st.nextToken();

                    dos.writeUTF("Enter the duration in seconds:");
                    int duration = Integer.parseInt(dis.readUTF());
                    LocalDateTime scheduledTime = LocalDateTime.now().plusSeconds(duration);

                    // Create a new thread for scheduling and sending the message
                    Thread schedulerThread = new Thread(() -> {
                        try {
                            // Sleep until the scheduled time
                            Thread.sleep(duration * 1000);

                            // Send the scheduled message to the recipient
                            sendMessage(scheduledMsgToSend, recipient);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            System.out.println("Invalid input" + e);
                        }
                    });

                    // Start the scheduler thread
                    schedulerThread.start();
                }

                // break the string into message and recipient part
                StringTokenizer st = new StringTokenizer(received, "#");
                String MsgToSend = st.nextToken();
                String recipient = st.nextToken();
 
                // search for the recipient in the connected devices list.
                // activeClients is a vector which is storing and managing all clients who are logged in.
                boolean recipientFound = false;
                boolean recipientLoggedIn = false;

                if (recipient.equals("all")) {
                    // Broadcast the message to all logged-in clients
                    for (ClientHandler mc : Server.activeClients) {
                        if (mc.isloggedin) {
                            mc.dos.writeUTF(this.name + " : " + MsgToSend);
                        }
                    }   
                } else {
                    // search for the recipient in the connected devices list.
                    for (ClientHandler mc : Server.activeClients) {
                        // if the recipient is found and logged in, write on its output stream
                        if (mc.name.equals(recipient) && mc.isloggedin) {
                            recipientFound = true;
                            recipientLoggedIn = true;
                            mc.dos.writeUTF(this.name + " : " + MsgToSend); // Print the message on the recipient's side
                            break;
                        }           
                    }

                    // If recipient not found in active client list, check in total client list
                    if (!recipientFound) {
                        for (ClientHandler mc : Server.totalClients) {
                            if (mc.name.equals(recipient)) {
                                recipientFound = true;
                                break;
                            }
                        }
                    }

                    // Send appropriate message to the client
                    if (!recipientFound) {
                    dos.writeUTF("The recipient does not exist.");
                    } else if (!recipientLoggedIn) {
                    dos.writeUTF("The recipient has logged out.");
                    }
                }

            } catch (IOException e) {
                 
                e.printStackTrace();
            } catch (NoSuchElementException e) {
                // Handle the exception if the message doesn't contain the expected delimiter
                System.out.println("Invalid message format: " + received);
            }
             
        }
        try
        {
            // closing resources
            this.dis.close();
            this.dos.close();
             
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    //String converters for the Client Handler class which can be used to print the names of the client as required in the two listing functions.
    //Edit these funtions and the constructor for any further data of the client which you wish to relay as per request.
    public String toName(){
        return "Client " + this.name;
    }

    @Override
    public String toString(){
        if(this.isloggedin){
            return "Client " + this.name + ", logged in";
        }
        return "Client" + this.name + ", logged out";
    }

    //Boolean checker
    public boolean isNameSet(){
        if(this.isnameset){
            return true;
        }
        return false;
    }

    // Method to send a message to a recipient
    private void sendMessage(String message, String recipient) throws IOException {
        boolean recipientFound = false;
        boolean recipientLoggedIn = false;

        // Search for the recipient in the active client list
        for (ClientHandler mc : Server.activeClients) {
            if (mc.name.equals(recipient) && mc.isloggedin) {
                recipientFound = true;
                mc.dos.writeUTF("[Scheduled Message] " + this.name + " : " + message);
                break;
            }
        }

        // If recipient not found in active client list, check in total client list
        if (!recipientFound) {
            for (ClientHandler mc : Server.totalClients) {
                if (mc.name.equals(recipient)) {
                    recipientFound = true;
                    recipientLoggedIn = false;
                    break;
                }
            }
        }

        // Send appropriate message to the client
        if (!recipientFound) {
            dos.writeUTF("The recipient does not exist.");
        } else if (!recipientLoggedIn) {
            dos.writeUTF("The recipient has logged out.");
        }
    }
}
