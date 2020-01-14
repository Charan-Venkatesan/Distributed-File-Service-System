// Student Name: Charan Venkatesan
// Student ID: 1001626250

//import statements
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

//Dispatcher class to handle sending files to all clients
class Dispatcher extends Thread{

    //queue to keep track of running threads
    Queue<ClientHandler> threads;

    //count to keep track of number of votes received from clients
    int voteCount = 0;

    //GUI componenets
    JFrame frame;
    JLabel msg;

    //constructor for Dispatcher class
    //inputs: Jframe and message Label
    //outputs:none
    //purpose: for setting frame and label values to access them from Dispatcher class
    Dispatcher(JFrame frame, JLabel msg){
        this.frame = frame;
        this.msg = msg;
    }

    //method that starts the Dispatcher thread
    //inputs: none
    //outputs: none
    //purpose: checks for completion status of threads and sends files to other clients
    public void run(){

        //outerIterator that checks for completion status of threads
        Iterator<ClientHandler> outerIterator;

        //innerIterator that is used to send notification and files to clients
        Iterator<ClientHandler> innerIterator;

        //ClientHandler objects
        ClientHandler outerThread;
        ClientHandler innerThread;

        while(true){

            //getting the concurrent linked queue of threads that are running
            threads = DBoxServer.threads;

            //initializing outer and inner iterators
            outerIterator = threads.iterator();
            innerIterator = threads.iterator();

            //traversing thread list
            while(outerIterator.hasNext()){

                //getting current Clienthandler object
                outerThread = outerIterator.next();

                //if client disconnected for this thread
                if(!outerThread.getClientStatus()){

                    //remove the thread from list and continue next iteration
                    threads.remove(outerThread);
                    continue;
                }

                //if current clientHandler thread completed its work
               if(outerThread.getStatus()){

                   //setting status to not completed
                   outerThread.setStatus(false);

                   //traversing threads list
                   while(innerIterator.hasNext()){

                       //getting current client handler object
                       innerThread = innerIterator.next();

                       //checking if it is not the same client that uploaded the file
                       if(!innerThread.equals(outerThread)){
                           try {

                               //if it is a new file
                               if(outerThread.type.equals("create")){

                                   //send New file signal to clients
                                   innerThread.out.writeObject("New file");
                               }

                               //if old file is modified
                               else if(outerThread.type.equals("modify")){

                                   //send Invalidation Notice to clients
                                   innerThread.out.writeObject("Invalidation Notice");
                               }

                               //if server is sending Vote Request from coordinator to other clients
                               else if(outerThread.type.equals("Vote Request")){
                                   innerThread.out.writeObject("Vote Request");
                               }

                               //if server is sending decision request from participant to other participants
                               else if(outerThread.type.equals("Decision Request")){
                                   if(!innerThread.equals(DBoxServer.coordinator)){
                                       innerThread.out.writeObject(outerThread.message);
                                       innerThread.out.flush();
                                       continue;
                                   }
                               }

                               //sending file to client and flushing output stream
                               innerThread.out.writeObject(outerThread.message);
                               innerThread.out.flush();

                           } catch (IOException e) {
                           }
                       }
                   }
               }

               //getting votes received count
               if(outerThread.isVoteStatus()){
                   voteCount++;
                   outerThread.setVoteStatus(false);
               }

               //if participant sends global msg
                if(outerThread.requestStatus){
                    try {
                        //sending global message to requestor participant
                        DBoxServer.requestor.out.writeObject(outerThread.message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            //if all clients voted
            if(voteCount == 2){

                //reseting vote count
                voteCount = 0;

                //notifying user about votes
                JOptionPane.showMessageDialog(frame, "All clients voted");
                msg.setText("All Clients voted");

                //sending votes to coordinator
                try {
                    DBoxServer.coordinator.out.writeObject("Votes");
                    DBoxServer.coordinator.out.writeObject(DBoxServer.votes);
                    DBoxServer.coordinator = null;

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

//Dropbox Server class
public class DBoxServer {

    public static ClientHandler requestor;
    //concurrent linked queue to keep track of running threads
    static Queue<ClientHandler> threads = new ConcurrentLinkedQueue<>();
    static ClientHandler coordinator;
    static Vector<String> votes = new Vector<>();

    //DBox Server main function
    //input: arguments
    //output: none
    //purpose: sets up the server socket and assigns thread to each incoming requests
    public static void main(String args[]) throws IOException {

        //GUI components
        JLabel msg = new JLabel();
        JLabel clientLabel = new JLabel("List of connected clients");
        JLabel status = new JLabel("Current Status: ");
        JLabel msg2 = new JLabel();
        JList clientsList = new JList();
        JPanel panel1 =new JPanel();
        JPanel panel2 = new JPanel();
        JPanel panel3 = new JPanel(new GridLayout(2,1));
        JPanel panel4 = new JPanel();
        JPanel outerPanel = new JPanel( new GridLayout(4,1,0,10));
        JFrame frame = new JFrame("Welcome to DBox Server");
        JButton shutdown = new JButton("ShutDown");

        //List for tracking connected clients
        Vector<String> connectedClients = new Vector<>();

        //port number on which server listens for incoming connections
        int port = 6066;

        //creating Server side socket to send and receive messages
        ServerSocket serverSocket = new ServerSocket(port);

        //setting text for msg
        msg.setText("Waiting for client on port " + serverSocket.getLocalPort() + "...");

        //setting size of texts
        msg.setFont(new Font(msg.getFont().getName(), msg.getFont().getStyle(), 20));
        msg2.setFont(new Font(msg2.getFont().getName(), msg2.getFont().getStyle(), 20));
        status.setFont(new Font(status.getFont().getName(), status.getFont().getStyle(), 20));
        clientLabel.setFont(new Font(clientLabel.getFont().getName(), clientLabel.getFont().getStyle(), 20));
        clientsList.setFont(new Font(clientsList.getFont().getName(), clientsList.getFont().getStyle(), 13));
        shutdown.setFont(new Font(clientsList.getFont().getName(), clientsList.getFont().getStyle(), 15));

        //setting up server GUI
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                shutdown.doClick();
            }
        });

        //setting size of frame
        frame.setSize(1920, 1080);

        //setting layout for server GUI
        frame.setLayout(new FlowLayout());

        //adding components to panel
        panel1.add(msg);
        panel2.add(status);
        panel2.add(msg2);
        panel3.add(clientLabel);
        panel3.add(clientsList);
        panel4.add(shutdown);

        //adding panels to outer panel
        outerPanel.add(panel1);
        outerPanel.add(panel2);
        outerPanel.add(panel3);
        outerPanel.add(panel4);

        //adding components to server GUI
        frame.add(outerPanel);

        //display the frame
        frame.setVisible(true);

        //outputstream and input stream declaration
        OutputStream outClient;
        ObjectOutputStream out;
        ObjectInputStream input;

        //New Thread for dispatching new file and invalidation notice messages to clients
        Thread Dispatcher = new Dispatcher(frame, msg2);
        Dispatcher.start();

        //server continuously listens for client request on the given port number
        while (true) {
            try {
                msg2.setText("");

                //accepting input connections from clients
                Socket server = serverSocket.accept();

                //getting object output stream
                outClient = server.getOutputStream();
                out = new ObjectOutputStream(outClient);

                //getting inputstream of the client request
                input = new ObjectInputStream(server.getInputStream());

                //creating new thread for the client
                msg2.setText("Assigning new Thread for the client : ");
                Thread T = new ClientHandler(server, input, out, frame, msg2, clientsList, connectedClients);

                //adding thread to concurrent linked queue
                threads.add((ClientHandler) T);

                //starting the thread
                T.start();

                shutdown.addActionListener(new ActionListener() {

                    //method to shut down the server
                    //inputs: Action Event object
                    //outputs: none
                    //purpose: allows server send shutdown notification to connected clients and shutdown the server
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        try {

                            //iterator to traverse the thread queue
                            Iterator<ClientHandler> it = threads.iterator();

                            //notifying connected clients that server is shutdown
                            while(it.hasNext()){
                                ClientHandler outClient = it.next();
                                outClient.out.writeObject("Server Shutdown");
                            }

                            //catching exceptions
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        System.exit(0);
                    }
                });
            }
            //catching IO exceptions
            catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Client Disconnected unexpectedly!");
                break;
            }
        }
    }
}

//Client handler class
class ClientHandler extends Thread {

    //variable declarations
    final Socket server;
    final ObjectInputStream ois;
    final ObjectOutputStream out;

    //GUI components
    final JFrame frame;
    JLabel msg;
    JList clientsList;
    Vector<String> connectedClients;
    String message;
    String clientName;
    private boolean status;
    String type;
    private boolean clientStatus;
    private boolean voteStatus;
    String deletedFilename;
    public boolean requestStatus;


    //initializing server, input stream, output stream, label, msg, connectedClients
    //input: socket, object input stream, Object output stream, Jframe, JLabel objects and connected clients vector
    //output: none
    //purpose: initializing all objects required for handling a client
    public ClientHandler(Socket server, ObjectInputStream ois, ObjectOutputStream out, JFrame frame, JLabel msg, JList clientsList, Vector<String> connectedClients) {
        this.server = server;
        this.ois = ois;
        this.out = out;
        this.frame = frame;
        this.msg = msg;
        this.clientsList = clientsList;
        this.connectedClients = connectedClients;
        setClientStatus(true);
    }

    //method to get vote status
    //inputs: none
    //outputs: voteStatus boolean variable
    //purpose: to get vote status of a client to check whether that client has voted or not
    public boolean isVoteStatus() {
        return voteStatus;
    }

    //method to set vote status
    //inputs: vote status boolean variable
    //outputs: none
    //purpose: sets the vote status of a client, used when the client has voted
    public void setVoteStatus(boolean voteStatus) {
        this.voteStatus = voteStatus;
    }



    //setter for client status
    //inputs: client status boolean variable
    //outpus: none
    //purpose: sets the status of clients whether it is connected or got disconnected
    public void setClientStatus(boolean clientStatus) {
        this.clientStatus = clientStatus;
    }

    //getter for client status
    //inputs: none
    //outputs: status of the client
    //purpose: provides the status of the client whether it is connected or got disconnected
    public boolean getClientStatus() {
        return clientStatus;
    }

    //method to run the thread
    //input: none
    //output: none
    //purpose: starts and runs the thread
    public void run() {
        try {
            while(true){

                //reads object from input stream
                message = (String) ois.readObject();

                //splitting message and identifier
                String splitMessage1[] = message.split(",",2);

                //if its a login credential
                if(splitMessage1[0].equals("Login")) {

                    //getting name of the client
                    String clientName = splitMessage1[1];

                    //notify about message and sets text for msg label
                    JOptionPane.showMessageDialog(frame, "Client Request received!!");

                    //checking if the client has already logged in and rejecting the connection
                    boolean isDuplicate = connectedClients.contains(clientName);
                    if (isDuplicate) {
                        out.writeObject("Duplicate client");
                        msg.setText("Rejected Connection from client");
                    }

                    //successful login of user
                    else {

                        //setting msgs in GUI
                        JOptionPane.showMessageDialog(frame, "Just connected to client: " + clientName);
                        msg.setText("Just connected to client: " + clientName);

                        //adding client name to list of connected clients
                        connectedClients.add(clientName);
                        clientsList.setListData(connectedClients);

                        //sending login successful message to client and closing the socket
                        out.writeObject("Success");
                    }
                }

                //when a client disconnects from server
                else if (message.contains("Disconnected")) {

                    //notify disconnect message
                    JOptionPane.showMessageDialog(frame, "Client message received!!");
                    String disconnectMsg = message.split(",")[0];
                    JOptionPane.showMessageDialog(frame, message);
                    setClientStatus(false);

                    //removing client name from connected clients list
                    connectedClients.remove(disconnectMsg);

                    //setting list data for showing updated list of clients connected
                    clientsList.setListData(connectedClients);

                    //closing input and output streams
                    ois.close();
                    out.close();
                    break;
                }

                //if file content is received which happens after client has logged in
                else if(message.contains("create") || message.contains("modify")) {

                    //notify client request
                    JOptionPane.showMessageDialog(frame, "Client message received!!");

                    //splitting identifier and content
                    String[] splitMessage = message.split("<", 2);

                    //splitting identifier to get client name and file name
                    String[] splitIdentifier = splitMessage[0].split(",", 3);
                    clientName = splitIdentifier[0];
                    String filename = splitIdentifier[1];
                    type = splitIdentifier[2];

                    //if it is a new file
                    if(type.equals("create")){

                        //notify about the new file uploaded to server
                        JOptionPane.showMessageDialog(frame, "A new file " + filename + " has been uploaded to server from " + clientName);
                    }

                    //if it is a modified file
                    else if(type.equals("modify")){

                        //notify about the modified file uploaded to server
                        JOptionPane.showMessageDialog(frame, "A client "+ clientName +" has modified the file " + filename);
                    }

                    //File Output Stream to take server backup of the file
                    FileOutputStream fos = new FileOutputStream("D:\\MS\\UTA\\CSE_5306_Distributed_Systems\\Lab3\\ServerFolder\\"+filename);
                    byte[] fileContent = splitMessage[1].getBytes();
                    fos.write(fileContent);

                    //setting message on GUI
                    msg.setText("Synchronizing file with other clients...");

                    //setting status of the thread as processing is completed and notification can be sent to clients
                    status=true;
                }

                //if Vote Request is received
                else if(message.contains("Deleted")){

                    //notify user of the vote request
                    JOptionPane.showMessageDialog(frame, "Vote Request received from coordinator");
                    msg.setText("Vote Request received from coordinator");
                    System.out.println("Vote Request Received");//TODO
                    type = "Vote Request";

                    //setting this client as coordinator
                    DBoxServer.coordinator = this;

                    //sending message to other clients
                    deletedFilename = message.split(",")[0];
                    msg.setText("Sending vote requests to other clients");
                    status = true;
                }

                //if vote is received
                else if(message.contains("Vote")){
                    //adding votes to list
                    splitMessage1 = message.split(",");
                    DBoxServer.votes.add(splitMessage1[1]);

                    //notifying Dispatcher that this client has voted
                    setVoteStatus(true);
                }

                //if global commit or global abort is received
                else if(message.equals("Global Commit") || message.equals("Global Abort")){
                    type = "Global";
                    status = true;

                    //if Global Abort is received
                    if(message.equals("Global Abort")){
                        FileInputStream fis = new FileInputStream("D:\\MS\\UTA\\CSE_5306_Distributed_Systems\\Lab3\\ServerFolder\\"+deletedFilename);

                        //getting size of the file to set the byte array size
                        int fileSize = (int) fis.getChannel().size();

                        //byte array for reading contents of the file
                        byte[] buffer = new byte[fileSize];

                        //using file input stream to read the contents of the file into buffer
                        fis.read(buffer);
                        fis.close();

                        //sending server backup of the file deleted to coordinator to restore it
                        type="Restore";
                        String identifier = clientName+","+deletedFilename+","+type+"<";
                        String fileContent = new String(buffer);
                        String sendMessage = identifier + fileContent;
                        out.writeObject("Restore");
                        out.writeObject(sendMessage);
                    }
                }

                //if decision request is received
                else if(message.equals("Decision Request")){
                    JOptionPane.showMessageDialog(frame, "Decision Request received");//TODO
                    type = "Decision Request";
                    status = true;
                    DBoxServer.requestor = this;
                }

                //if global message from participant is received
                else if(message.startsWith("GlobalMsg,")){
                    JOptionPane.showMessageDialog(frame, "Global message from participant received");//TODO
                    message = message.split(",")[1];
                    requestStatus = true;
                }
            }

            //catching exceptions
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    //getter for status of the thread
    //inputs: none
    //outputs: none
    //purpose: returns the status of the thread
    public boolean getStatus() {
        return status;
    }

    //setter for status of the thread
    //inputs: status boolean value
    //outputs: none
    //purpose: sets the status of the thread
    public void setStatus(boolean status) {
        this.status=status;
    }
}


