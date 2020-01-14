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
import java.math.BigInteger;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Random;
import java.util.Vector;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.exit;
import static java.lang.Thread.sleep;
import static java.nio.file.StandardWatchEventKinds.*;

//DBoxClient class
public class DBoxClient extends JDialog{

    //GUI components
    private JTextField usernameTxt;
    private JLabel usernameLbl;
    private JButton loginBtn;
    private JButton cancelBtn;

    //variable declaration for success to check authentication
    private boolean success;

    //variable declaration to check the status of the client
    private static boolean status;

    //variable declaration for keeping track of timeout period
    long voteRequestTimeout;
    long globalMsgTimeout;

    //variable declaration to store global commit or abort message
    String globalMsg;

    //method to check the status of client
    //inputs: none
    //outputs: boolean value that indicates the status of the client
    //purpose: to check whether client is connected to server or got disconnected from server
    public static boolean isStatus() {
        return status;
    }

    //method that sets the status of the client
    //inputs: boolean value
    //outputs: none
    //purpose: sets the status of the client when it is connected and when it is getting disconnected
    public static void setStatus(boolean status) {
        DBoxClient.status = status;
    }

    //hashmap that keeps tracks of MD5 hashes of files that are downloaded by client to check for synchronization
    static HashMap<String,String> fileHashes = new HashMap<>();

    //constructor of DBoxClient
    //inputs: Jframe object, Object Output Stream object and Object Input Stream Object
    //outputs: none
    //purpose: creates login modal and handles authentication part
    public DBoxClient(Frame parent, ObjectOutputStream out, ObjectInputStream ois){

        //calling super() method to create Login modal
        super(parent, "Login", true);
        usernameLbl = new JLabel("Username: ");
        usernameTxt = new JTextField(20);

        //creating panel and setting layout of the panel
        JPanel panel = new JPanel(new FlowLayout());

        //setting font size of username label and textfield
        usernameTxt.setFont(new Font(usernameTxt.getFont().getName(),usernameTxt.getFont().getStyle(),20));
        usernameLbl.setFont(new Font(usernameLbl.getFont().getName(),usernameLbl.getFont().getStyle(),20));

        // adding username label and textbox to panel
        panel.add(usernameLbl);
        panel.add(usernameTxt);

        //creating login button
        loginBtn = new JButton("Login");
        loginBtn.setFont(new Font(loginBtn.getFont().getName(),loginBtn.getFont().getStyle(),15));

        //adding action listener to login button to forward the message to server
        loginBtn.addActionListener(new ActionListener() {

            //actionPerformed method for Action Listener
            //input: ActionEvent object
            //outputs: message containing authentication success/failure information
            //purpose: on clicking login button this method takes the client username and send it to server to perform authentication
            public void actionPerformed(ActionEvent e) {
                try {
                    //writing username to outputstream
                    out.writeObject("Login,"+getUsername());
                    out.flush();

                    //reading success/failure message from inputstream after server performs authentication process
                    String readObj = (String) ois.readObject();

                    //if authentication sucess
                    if(readObj.equals("Success")){

                        //shows user the success message and closes the login modal
                        JOptionPane.showMessageDialog(DBoxClient.this,
                                "Hi " + getUsername() + "! You have successfully logged in.",
                                "Login",
                                JOptionPane.INFORMATION_MESSAGE);

                        //assigns success to true indicating successful authentication
                        success = true;
                        dispose();
                    }

                    //if client receives duplicate message since client has already logged in or another client uses same name
                    else if(readObj.equals("Duplicate client")){

                        //shows duplicate message to user and goes back to login modal
                        JOptionPane.showMessageDialog(DBoxClient.this,
                                "Server rejected connection due to duplicate username",
                                "Login",
                                JOptionPane.ERROR_MESSAGE);

                        //setting success to false indicating failed authentication
                        success = false;
                        usernameTxt.setText("");
                    }

                    //if client receives incorrect username message
                    else{

                        //shows incorrect username message and goes beck to login modal
                        JOptionPane.showMessageDialog(DBoxClient.this,
                                "Server rejected connection due to incorrect username",
                                "Login",
                                JOptionPane.ERROR_MESSAGE);

                        // reset username
                        usernameTxt.setText("");

                        //setting success to false indicating failed authentication
                        success = false;
                    }
                    //catching exceptions
                } catch (IOException | ClassNotFoundException ex) {
                    ex.printStackTrace();
                }
            }
        });

        //creates cancel button and sets size of the button to close the login modal
        cancelBtn = new JButton("Cancel");
        cancelBtn.setFont(new Font(cancelBtn.getFont().getName(),cancelBtn.getFont().getStyle(),15));

        //adding action listener to cancel button to perform cancel operation
        cancelBtn.addActionListener(new ActionListener() {

            //actionPerformed method for cancel button
            //input: ActionEvent object
            //output: none
            //purpose: closes the login modal and exit the application
            public void actionPerformed(ActionEvent e) {
                dispose();
                exit(0);
            }
        });

        //creating panel for buttons
        JPanel bp = new JPanel();

        //adding buttons to panel
        bp.add(loginBtn);
        bp.add(cancelBtn);

        //adding textbox panel and button panel to login modal
        getContentPane().add(panel, BorderLayout.CENTER);
        getContentPane().add(bp, BorderLayout.PAGE_END);

        //packing modal and setting location
        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    //method to get username of client
    //input: none
    //output: username of client
    //purpose: to retrieve username of client
    public String getUsername() {
        return usernameTxt.getText().trim();
    }

    //method to determine authentication status
    //input: none
    //output: success variable
    //purpose: this method returns the status of the authentication upon which further processes will be carried out
    public boolean isLoginSuccess() {
        return success;
    }

    //method that listens for New File notification or Invalidation notice from server
    //inputs: Object Input Stream, JFrame, JLabel to display status, directory in which the files should be downloaded, contentlist to update contents of shared directory, folder and JList
    //outputs: none
    //purpose: Looks for notification from server, downloads files into shared directory and refreshes the shared directory contents list in GUI and check for timeouts during two phase commit and sends messages accordingly
    public void getMessage(ObjectInputStream ois, ObjectOutputStream out, JFrame frame, JLabel msg2, Path dir, Vector<String> contentList, File folder, JList filesList, FileWatcher fw) throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException {
        String recvdMessage = "";
        String decision;
        String filename = "";
        voteRequestTimeout = currentTimeMillis();

        //loop until the client disconnects
        while (isStatus()) {

            //check for messages from server
            String readOis = (String) ois.readObject();

            //if server sends New file or Invalidation Notice or Restore messages
            if (readOis.equals("New file") || readOis.equals("Invalidation Notice") || readOis.equals("Restore")) {

                //if it is New file notification
                if (readOis.equals("New file")) {

                    // Notify user of the new file
                    JOptionPane.showMessageDialog(frame, "New file Notification from Server!");
                }

                //if it is an Invalidation Notice
                else if (readOis.equals("Invalidation Notice")) {

                    //Notify user of Invalidation Notice
                    JOptionPane.showMessageDialog(frame, "Invalidation Notice from Server!");
                }

                recvdMessage = (String) ois.readObject();

                //set text for msg2
                msg2.setText("Downloading file from server...");

                //splitting identifier and content
                String[] splitMessage = recvdMessage.split("<", 2);
                String recvdContent = splitMessage[1];

                //splitting identifier to get client name and file name
                String[] splitIdentifier = splitMessage[0].split(",", 3);
                String recvdFilename = splitIdentifier[1];

                //assigning file output stream path to download file
                String fosPath = dir + "/" + recvdFilename;
                FileOutputStream fos = new FileOutputStream(fosPath);

                //convert content to byte array to write it file
                byte[] contentBuffer = recvdContent.getBytes();

                //creating MD5 object
                MessageDigest md5 = MessageDigest.getInstance("MD5");

                //getting MD5 hash of the file that is downloaded
                byte[] recvdHash = md5.digest(contentBuffer);
                BigInteger recvdHashNo = new BigInteger(1, recvdHash);
                String recvdHashText = recvdHashNo.toString(16);
                while (recvdHashText.length() < 32) {
                    recvdHashText = "0" + recvdHashText;
                }

                //storing MD5 hash of the file in hashmap
                fileHashes.put(recvdFilename, recvdHashText);

                //writing content to file
                fos.write(contentBuffer);

                //closing file output stream
                fos.close();

                //Notifying user about downloaded file
                JOptionPane.showMessageDialog(frame, recvdFilename + " is downloaded from server");
                msg2.setText(recvdFilename + " is downloaded from server");

                //clearing content list for adding updated list
                contentList.clear();
                String files;

                //getting updated file contents from shared directory and adding them to list
                File[] listOfFiles2 = folder.listFiles();
                for (int i = 0; i < listOfFiles2.length; i++) {
                    files = listOfFiles2[i].getName();
                    contentList.add(files);
                }

                //setting list data
                filesList.setListData(contentList);
            }

            //if the message is for two phase commit
            else {
                if (fw.voteTimeout != 0) {
                    //checking if timeout expired for getting votes
                    if (System.currentTimeMillis() - fw.voteTimeout > 100000) {

                        //sending Global Abort message to all clients
                        decision = "Global Abort";
                        JOptionPane.showMessageDialog(frame, "Final Decision: " + decision + " because of timeout");
                        msg2.setText("Final Decision: " + decision + " because of timeout");
                        out.writeObject(decision);
                        ois.readObject();
                    }

                    //if votes are received
                    else if (readOis.equals("Votes")) {

                        //checking whether all clients voted yes
                        Vector<String> votes = (Vector<String>) ois.readObject();
                        JOptionPane.showMessageDialog(frame, "Votes Received from other clients");
                        msg2.setText("Checking votes");
                        if (votes.contains("No")) {
                            decision = "Global Abort";
                            JOptionPane.showMessageDialog(frame, "Final Decision: " + decision);

                        } else {
                            decision = "Global Commit";
                            JOptionPane.showMessageDialog(frame, "Final Decision: " + decision);
                        }

                        //sending final decision to other clients
                        msg2.setText("Final Decision: " + decision);
                        out.writeObject(decision);
                    }
                }
                else {

                    //if vote request is received
                    if (readOis.equals("Vote Request")) {

                        //waiting for 3 seconds before voting
                        sleep(3000);

                        //randomly vote yes or no for deletion
                        JOptionPane.showMessageDialog(frame, "Vote Request from Coordinator!");
                        msg2.setText("Voting for deletion");
                        recvdMessage = (String) ois.readObject();
                        filename = recvdMessage.split(",")[0];
                        Random rand = new Random();
                        boolean vote = rand.nextBoolean();
                        if (vote) {
                            out.writeObject("Vote,Yes");
                        } else {
                            out.writeObject("Vote,No");
                        }

                        //setting timeout for receiving global message
                        globalMsgTimeout = currentTimeMillis();
                    }
                    else{

                        //checking if timeout for receiving global message expired
                        if(currentTimeMillis() - globalMsgTimeout > 100000){
                            JOptionPane.showMessageDialog(frame, "Timed out Global msg");//TODO
                            out.writeObject("Decision Request");
                        }

                        //if Global Commit is received from coordinator
                        else if (readOis.equals("Global Commit")) {

                            //deleting file from shared directory
                            globalMsg = "Global Commit";
                            filename = recvdMessage.split(",")[0];
                            File file = new File(dir + "/" + filename);
                            file.delete();
                            fileHashes.remove(filename);
                            fw.deleteStatus = false;
                            JOptionPane.showMessageDialog(frame, filename + " deleted");
                            msg2.setText(filename + "deleted");

                            //clearing content list for adding updated list
                            contentList.clear();
                            String files;

                            //getting updated file contents from shared directory and adding them to list
                            File[] listOfFiles2 = folder.listFiles();
                            for (int i = 0; i < listOfFiles2.length; i++) {
                                files = listOfFiles2[i].getName();
                                contentList.add(files);
                            }

                            //setting list data
                            filesList.setListData(contentList);
                        }

                        //if Global Abort is received
                        else if(readOis.equals("Global Abort")){
                            globalMsg = "Global Abort";
                        }
                        else if(readOis.equals("Decision Request")){
                            if(globalMsg != null){
                                out.writeObject("GlobalMsg,"+globalMsg);
                            }

                        }
                    }
                }
            }
        }
    }

    //main function of DBox client
    //input: arguments
    //output: none
    //purpose: creates instance for DBox client, thread for watching files, initiates call to getMessage method and carries out processes if authentication is successful
    public static void main(String args[]){

        //creating JFrame for client
        final JFrame frame = new JFrame("Welcome to DBox");

        //creating panels
        JPanel panel1 = new JPanel();
        JPanel panel2 = new JPanel();
        JPanel panel3 = new JPanel();
        GridLayout gl1 = new GridLayout(7,1);
        gl1.setVgap(0);
        JPanel panel4 = new JPanel(new GridLayout(3,1));
        JPanel panel5 = new JPanel();
        JPanel outerPanel = new JPanel(gl1);

        //GUI components
        final JButton btnLogin = new JButton("Click to login");
        JLabel msg = new JLabel();
        JLabel status = new JLabel("Current Status:");
        JLabel msg2 = new JLabel();
        JLabel filesListLabel1 = new JLabel();
        JLabel filesListLabel2 = new JLabel();
        JList filesList = new JList();
        JButton kill = new JButton("Disconnect");

        //setting size of components
        msg.setFont(new Font(msg.getFont().getName(), msg.getFont().getStyle(), 20));
        msg2.setFont(new Font(msg2.getFont().getName(), msg2.getFont().getStyle(), 20));
        status.setFont(new Font(status.getFont().getName(), status.getFont().getStyle(), 20));
        filesListLabel1.setFont(new Font(filesListLabel1.getFont().getName(), filesListLabel1.getFont().getStyle(), 20));
        filesListLabel2.setFont(new Font(filesListLabel2.getFont().getName(), filesListLabel2.getFont().getStyle(), 20));
        filesList.setFont(new Font(filesList.getFont().getName(), filesList.getFont().getStyle(), 13));
        kill.setFont(new Font(kill.getFont().getName(), kill.getFont().getStyle(), 15));

        //server name and port number declarations for client
        String serverName = "localhost";
        int port = 6066;

        //variable declaration to store filenames of the client's directory
        String files = "";

        //Vector to keep track of contents in the client's shared directory
        Vector<String> contentList = new Vector<>();
        DBoxClient.setStatus(true);

        //setting layout for frame
        FlowLayout flowLayout = new FlowLayout();
        flowLayout.setVgap(0);
        frame.setLayout(flowLayout);

        //adding components to panels
        panel1.add(btnLogin);
        panel2.add(msg);
        panel3.add(status);
        panel3.add(msg2);
        panel4.add(filesListLabel1);
        panel4.add(filesListLabel2);
        panel4.add(filesList);
        panel5.add(kill);

        //adding panels to outer panel
        outerPanel.add(panel1);
        outerPanel.add(panel2);
        outerPanel.add(panel3);
        outerPanel.add(panel4);
        outerPanel.add(panel5);

        //adding outer panel to frame
        frame.add(outerPanel);

        //display frame
        frame.setVisible(true);
        try{

            //setting text for msg label
            msg.setText("Connecting to " + serverName + " on port " + port);

            //creating client socket for login to send and receive messages
            Socket client = new Socket(serverName, port);


            //creating outputstream to send messages for login
            OutputStream outToServer = client.getOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(outToServer);

            //creating object input stream to read messages for login
            ObjectInputStream ois = new ObjectInputStream(client.getInputStream());

            //creating instance for DBoxClient and calling the constructor to perform authentication process
            DBoxClient dBoxClient = new DBoxClient(frame, out, ois);

            //updating text of msg label
            msg.setText("Just connected to " + client.getRemoteSocketAddress());
            msg2.setText("Just connected to " + client.getRemoteSocketAddress());

            //to display the frame
            dBoxClient.setVisible(true);

            //if authentication is successful
            if(dBoxClient.isLoginSuccess()) {

                //getting client name by calling getuserName method
                String clientName = dBoxClient.getUsername();

                //adding action listener for kill/disconnect button to perform disconnect operations
                kill.addActionListener(new ActionListener() {
                    @Override
                    //action performed method for kill/disconnect button
                    //input: Action Event object
                    //output: none
                    //purpose: disconnect from server, closes the frame and exit the program
                    public void actionPerformed(ActionEvent e) {
                        try {

                            //sending disconnect message to server to let server know that this client is disconnected
                            out.writeObject(clientName + ",Disconnected");
                            out.flush();
                            DBoxClient.setStatus(false);

                            //closing frame, output stream, client socket and exiting the program
                            exit(0);
                            frame.dispose();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                });

                //setting size of frame
                frame.setSize(1920, 1080);

                //setting closing event
                frame.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent we) {
                        kill.doClick();
                    }
                });

                //setting text  and size of button
                btnLogin.setText("Hi " + clientName + "!");
                btnLogin.setFont(new Font(btnLogin.getFont().getName(),btnLogin.getFont().getStyle(),15));

                //creating watch service to watch the shared directory for file creation and file updates
                WatchService watcher = FileSystems.getDefault().newWatchService();

                //path variable to set the path to be watched by watch service
                Path dir = null;

                //creating shared directory for client
                dir = Paths.get("D:/MS/UTA/CSE_5306_Distributed_Systems/Lab3/"+clientName+"Folder");
                Files.createDirectories(dir);
                String filePath = dir.toString();

                //setting text for file list label
                filesListLabel1.setText("Contents of the shared directory:");
                filesListLabel2.setText(filePath);

                //creating file instance to extract list of files from the shared directory
                File folder = new File(filePath);

                //creating key and setting kind to watch for creation of files and updates to files in shared directory
                WatchKey key = dir.register(watcher, ENTRY_MODIFY, ENTRY_DELETE);

                //getting list of files in the shared directory
                File[] listOfFiles1 = folder.listFiles();

                //getting filenames from the list to display in the frame
                for(int  i = 0;i < listOfFiles1.length;i++){
                    files = listOfFiles1[i].getName();
                    contentList.add(files);
                }

                //setting list data for fileList which will display the list of files in the shared directory
                filesList.setListData(contentList);

                //thread to watch the shared directory for file updates
                Thread T1 = new FileWatcher(key, dir, clientName, out, frame, msg2, client, contentList, folder, filesList,fileHashes);
                T1.start();

                //method call to look for notification from client
                dBoxClient.getMessage(ois, out, frame, msg2, dir, contentList, folder, filesList, (FileWatcher) T1);
            }
        }

        //catching exceptions
        catch (UnknownHostException e) {
                e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException | NoSuchAlgorithmException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}

//class that handles events when file is created or updated in the shared directory
class FileWatcher extends Thread{

    //variable declarations
    private final HashMap<String, String> fileHashes;
    public boolean deleteStatus = true;
    WatchKey key;
    Path dir;
    String clientName;
    ObjectOutputStream out;
    JFrame frame;
    JLabel msg2;
    Socket client;
    Vector<String> contentList;
    File folder;
    JList filesList;
    DBoxClient dBoxClient;
    long voteTimeout;

    //constructor of FileWatcher class
    //inputs: Watchkey, directory, client name, object output stream, JFrame, JLabel, client's socket, list that tracks contents of directory, folder, files list, hashmap that has MD5 hashes of files
    //outputs: none
    //purpose: initializes all the variables of File Watcher class
    public FileWatcher(WatchKey key, Path dir, String clientName, ObjectOutputStream out, JFrame frame, JLabel msg2, Socket client, Vector<String> contentList, File folder, JList filesList, HashMap<String, String> fileHashes){
        this.key = key;
        this.dir = dir;
        this.clientName =clientName;
        this.out = out;
        this.frame = frame;
        this.msg2 = msg2;
        this.client = client;
        this.contentList = contentList;
        this.folder = folder;
        this.filesList = filesList;
        this.fileHashes = fileHashes;
    }

    //method that runs the thread
    //inputs: none
    //outpus: none
    //purpose: allows the thread to perform required operations like getting file from directory and uploading to server
    public void run() {
        try {

            //MD5 hash to store MD5 hash of files
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String message, content, files;

            //loop for watching the shared directory for file creation and file updates
            while (true) {

                //pauses execution to allow time for larger files to get copied to the shared directory
                sleep(1000);

                //loop that looks for create file and modify file kind
                for (WatchEvent<?> event : key.pollEvents()) {

                    //pauses execution to avoid multiple events to be raised for the same file
                    //this might happen sometimes during file creation
                    sleep(1000);
                    WatchEvent.Kind<?> kind = event.kind();

                    //if overflow occurs continue to next iteration
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    else if(kind == ENTRY_MODIFY){
                        // The filename is the context of the event.
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path fileName = ev.context();

                        //creating absolute path with file name and directory to read the file
                        String file = dir.toString() + "/" + fileName.toString();

                        if(Files.exists(Paths.get(file))){
                            //Basic File Attributes object to get the attributes of the file
                            BasicFileAttributes attr = Files.readAttributes(Paths.get(file), BasicFileAttributes.class);
                            String type;

                            //finding if it is create or modify using creation time and modified time of the file
                            if(attr.creationTime().compareTo(attr.lastModifiedTime()) >= 0){
                                type = "create";
                            }
                            else{
                                type = "modify";
                            }

                            //file input stream to read file contents from the file created in shared directory
                            FileInputStream fis = null;
                            fis = new FileInputStream(file);

                            //getting name of the file
                            String filename = fileName.toString();

                            //creating identifier that is used by server to differentiate clients, allows server to use the file name and let server know whether its a create or modify
                            String identifier = clientName + "," + filename + "," + type + "<";

                            //getting size of the file to set the byte array size
                            int fileSize = (int) fis.getChannel().size();

                            //byte array for reading contents of the file
                            byte[] buffer = new byte[fileSize];

                            //using file input stream to read the contents of the file into buffer
                            fis.read(buffer);
                            fis.close();

                            //if the MD5 hash of the file is not already present in the hashmap
                            if(fileHashes.get(filename) == null ) {

                                //converting byte array to string to send it to server
                                content = new String(buffer);

                                //concatenating identifier with content
                                message = identifier + content;

                                //sending message to output stream
                                out.writeObject(message);
                                out.flush();
                                msg2.setText("File uploaded to server");
                            }

                            //if the MD5 hash of the file is already present in the hashmap
                            else{
                                String  storedFileHash = fileHashes.get(filename);

                                //getting MD5 hash of the file
                                byte[] fileHash = md5.digest(buffer);
                                BigInteger fileHashNo = new BigInteger(1, fileHash);
                                String fileHashText = fileHashNo.toString(16);
                                while(fileHashText.length() < 32){
                                    fileHashText = "0" + fileHashText;
                                }

                                //if the MD5 hash in the hashmap and the MD5 hash of the file are not the same
                                if(!storedFileHash.equals(fileHashText)){

                                    //update the MD5 hash in the hashmap
                                    fileHashes.put(filename, fileHashText);

                                    //converting byte array to string to send it to server
                                    content = new String(buffer);

                                    //concatenating identifier with content
                                    message = identifier + content;

                                    //sending message to output stream
                                    out.writeObject(message);
                                    out.flush();
                                    msg2.setText("File uploaded to server");
                                }
                            }
                        }
                    }

                    //if file is deleted from shared directory
                    else if(kind == ENTRY_DELETE){
                        if(deleteStatus){
                            //getting filename of the file deleted
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path fileName = ev.context();
                            message = fileName.toString() + ",Deleted";
                            JOptionPane.showMessageDialog(frame, message);
                            msg2.setText(message);

                            //Notifying user that client has assumed the role of coordinator
                            JOptionPane.showMessageDialog(frame, "Assumed the role of coordinator");
                            msg2.setText("Assumed the role of coordinator");

                            //sending Vote Requests
                            out.writeObject(message);
                            System.out.println("delete message sent");//TODO

                            //setting timeout value for receiving votes
                            voteTimeout = currentTimeMillis();
                        }
                        else{
                            deleteStatus = true;
                        }
                    }
                }

                //reseting key and breaking loop if key is not valid
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
            //catching exceptions
        } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}

