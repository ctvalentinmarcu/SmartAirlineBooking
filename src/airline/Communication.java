/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package airline;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.SwingUtilities;


/**
 *
 * @author vali
 */
public class Communication extends Thread {
    public static final int REQUEST = 0;
    public static final int REPLY = 1;    
    
    int PEERS = 2;
    int ZONES = 3;
    int ttl = 3;
    String type;
    int resultId = 0;
    GUI gui;
    int ID;
    int port;    
    String status;
    boolean finish=false;
    boolean readyToPrintNeighbours = false;
    
    boolean neighbours[];
    ArrayList<ArrayList<Integer>> next;  // zone routing table
    int zone[]; // which zone does each peer belong to
    int last[]; // last[i] = last destination requested from i
    
    ArrayList<ObjectOutputStream> outputstreams;
    ArrayList<ObjectInputStream> inputstreams;
    
    //CommunicationProtocol protocol;
    ConcurrentLinkedQueue<Message> messagesToSend;
    
    public Communication(GUI gui, int ID, int peers){
        this.gui = gui;
        this.PEERS = 7;//peers;
        this.ID = ID;
        //this.ttl = ttl;
        this.port = 5000 + ID;
        this.type = "dOPT";
        
        this.neighbours = new boolean[PEERS];
        //this.ZONES = (PEERS%2==0) ? (PEERS/2) : (1+PEERS/2);
        this.ZONES = 3;
        this.next = new ArrayList<ArrayList<Integer>>();
        for(int i=0; i<PEERS; i++) next.add(new ArrayList<Integer>());
        
        this.last = new int[PEERS];
        for(int i=0; i<PEERS; i++) last[i] = -1;
        
        this.zone = new int[PEERS];

        inputstreams = new ArrayList<ObjectInputStream>(PEERS);
        outputstreams = new ArrayList<ObjectOutputStream>(PEERS);
        messagesToSend = new ConcurrentLinkedQueue<Message>();
        
        this.finish = false;
        this.readyToPrintNeighbours = false;
    }
    
    public void submitCommandFromGUI(String command,  int ttl){
        this.ttl = ttl;
        //System.out.println("received command "+command+" from GUI");
        //this.protocol.submitCommandFromGUI(command, ttl);
        int type = REQUEST; // 0
        int source = this.ID;
        int destination = Integer.parseInt(command); // we assume command is an integer
        ArrayList<Integer> targets = new ArrayList<Integer>();
        targets.add(new Integer(this.zone[this.ID])); // add my zone
        targets.addAll(this.next.get(zone[destination])); // add all nextHops to destination
        
        for(int i=0; i<PEERS; i++) 
            if(i != this.ID)
                if(this.neighbours[i]==true)
                    if(targets.contains(this.zone[i])) {
                    //if(targets.contains(this.zone[destination])) {
            Message m = new Message();
            m.type = REQUEST;
            m.source = source;
            m.destination = destination;
            m.ttl = ttl;
            //m.path.addAll(message.path);
            m.path = new ArrayList<Integer>();
            m.path.add(new Integer(this.ID));
            m.via = this.ID;
            m.next = i;
            submitMessage(m);
        }             
       // reset the counter of found paths
        this.resultId = 0;
        //submitMessage(m);
    }
    public synchronized void submitCommandFromPeer(Message message){
        //System.out.println("received command "+messsage+" from Peer");
        //this.protocol.submitCommandFromPeer(messsage);
        int type = message.type;
        if(type==REPLY && message.source==this.ID){
            printResult(message.path);
            return;
        }
        if(type==REPLY){
            //message.path.add(0, this.ID); // add myself to path
            message.via = last[message.source];
            message.next = message.via;
            submitMessage(message);
            return;
        }
         // type==REQUEST            
        if(message.destination == this.ID){
            message.path.add(this.ID);
            message.type = REPLY;
            //message.via = last[message.source];
            message.next = message.via;
            submitMessage(message);
            return;
        }
        // check the TTL
        message.ttl--;
        if(message.ttl == 0) return;
           
        
        //check whether there is a loop in the path
        for(Integer i : message.path) 
            if(i.intValue()==this.ID) return; 
        
        last[message.source] = message.via;
        // initialize target zones
        ArrayList<Integer> targets = new ArrayList<Integer>();
        targets.add(new Integer(this.zone[this.ID])); // add my zone
        targets.addAll(this.next.get(zone[message.destination])); // add all nextHops to destination
        
        for(int i=0; i<PEERS; i++) 
            if(i != message.via) if(i != this.ID)
                if(this.neighbours[i]==true)
                    if(targets.contains(this.zone[i])) {
                    //if(targets.contains(this.zone[message.destination])) {
            Message m = new Message();
            m.type = REQUEST;
            m.source = message.source;
            m.destination = message.destination;
            m.ttl = message.ttl;
            m.path = new ArrayList<Integer>();
            m.path.addAll(message.path);
            m.path.add(this.ID);
            m.via = this.ID;
            m.next = i;
            submitMessage(m);
        }
        return;        
    }
    
    public void submitMessage(Message m){
        try {this.messagesToSend.add(m);} catch(Exception e) {System.out.println("COMM: exception messagesToSend");}
        System.out.println("COMM: received command "+m.destination+" from "+m.source);
    }
    
    public void printResult(ArrayList<Integer> path){
        resultId++;
        String result = "\n Path "+this.resultId+". ";
        if(this.resultId == 1)
            result = "\n\n Flights between "+this.ID+" and "+path.get(path.size()-1)+":\n" + result;
        for (Integer i : path){
            result += i+" - ";
        }       
        this.gui.TextboxUpdate(result);
    }
    
    @Override
    public void run(){
        this.gui.updateStatus("Welcome"); 
        try{ Thread.sleep(2000); } catch (Exception ex) {}
                        
        // set neighnours
        setNeighbours();
        // set zone belonging for each airport
        readZone();
        //read next hops from the current zone to any other zone
        readNextHops();
        
        // connect to all the other peers
        initializeConnections(type, ID);        
        this.gui.updateStatus("Connection established with everyone. You may start writing.");

        // start the handler threads for incoming messages  
        int ii = 0;
        for(ObjectInputStream ois : inputstreams){// if(this.neighbours[ii]==true){
            InputHandler handler = new InputHandler(this, ois);
            handler.start();
        }       
        
        
        while(this.finish == false){
            Message message;        
        
            // send to destination all the messages in the queue 
            if(PEERS > 1) while (!(this.messagesToSend.isEmpty())){
                message = this.messagesToSend.poll();
                System.out.println("=== INITIAL === COMM_RUN: received command "+message.destination+" from GUI");
                int id=message.next;

                if(id > this.ID) id--;

                try{
                    if(id >= 0) {
                        this.outputstreams.get(id).writeObject(message);
                        this.outputstreams.get(id).flush();
                    }                         
                }
                catch (Exception e) {System.out.println("Error while sending a message to peer "+id);}  
            }   
            // check if neighbours need to be printed to the GUI
            if(this.readyToPrintNeighbours==true){                
                this.printNeighbours();
                this.readyToPrintNeighbours = false;
            }
        }
    }
    
    public void setNeighbours(){
        int ref = (PEERS%2==0) ? (PEERS) : (PEERS-1);  
        int step = (ID + PEERS/2) % ref;
        if(this.ID > 0) this.neighbours[ID-1] = true;
        if(this.ID < PEERS-1) this.neighbours[ID+1] = true;        
        if(this.ID < PEERS-1) this.neighbours[step] = true;
        // alternative: read neighbours from file (TODO)
    }
    
    // read which zone each node is assigned to
    public void readZone(){
        // for start, just set them statically
        int i;
        for(i=0;i<PEERS;i++)
            zone[i] = i/ZONES;
    }
    
    // read zone routing table
    public void readNextHops(){
        // for start, just set them statically
        int i;
        for(i=0;i<ZONES;i++)
            if(i<zone[ID]) next.get(i).add(new Integer(zone[ID]-1));
            else if(i>zone[ID]) next.get(i).add(new Integer(zone[ID]+1));
            else next.get(i).add(new Integer(zone[ID]));
        //System.out.println("setNextHops: here are my next hops: "+next.get(0).get(0)+next.get(1).get(0)+next.get(2).get(0));
    }
    
    // print neighbours
    public void printNeighbours(){
        String text = "\n\n My neighbours: ";
        int i;
        for(i=0;i<PEERS;i++)
            if(this.neighbours[i]==true)
                text += i+" ";
        text+="\n\n";
    }
    
    public void initializeConnections(String type, int ID){
        int i=0;
        // we establish connections as client with 0...ID-1
        if(ID > 0)
            for (i=0; i<ID; i++) {
                this.gui.updateStatus("Trying to establish connection as client to peer"+i);                
                try {
                    String ip = "localhost";
                    int port = 5000 + i*100 + ID;
                    //this.gui.updateStatus("Trying to establish connection as client to "+i);
                    connectToServer(ip, port, i);
                    this.gui.updateStatus("Succesfully connected as client to peer "+i);
                    Thread.sleep(2000);
                } 
            catch (Exception e) {e.printStackTrace(); System.out.println(ID+": eroare la conexiunea client cu "+i);}
            }
        // we establish connections as server with ID+1 ... PEERS-1
        if(ID < PEERS-1)
            for (i=ID+1; i<PEERS; i++) {
                this.gui.updateStatus("Trying to establish connection as server to peer "+i);
            try{
                int port = 5000 + ID*100 + i;
                ServerSocket ss =  new ServerSocket(port); 
                //this.gui.updateStatus("Trying to establish connection as server to "+i);
                connectToClient(ss, i);
                this.gui.updateStatus("Succesfully connected as server to peer "+i);
                Thread.sleep(2000);
            }
            catch (Exception e) {e.printStackTrace(); System.out.println(ID+": eroare la conexiunea server cu "+i);}
            }         
    }
    
    
    public void connectToClient(ServerSocket ss, int idClient) {
        Socket client = null;
    try{
        //accepts incoming client (blocking function)
        client = ss.accept();    

        //get the output stream
        OutputStream output = client.getOutputStream();
        
        //get the input stream
        InputStream input = client.getInputStream();

        //wrap the output stream in an object output stream to easily write objects
        ObjectOutputStream objectOutput = new ObjectOutputStream(output);
        objectOutput.flush();

        //add the output stream to the list fo client output streams
        this.outputstreams.add(objectOutput);
        
        //wrap the input stream in an object input stream to easily write objects
        ObjectInputStream objectInput = new ObjectInputStream(input);

        //add the input stream to the list of client input streams
        this.inputstreams.add(objectInput);            
        }
        catch (Exception e) {
            //e.printStackTrace(); 
            this.gui.updateStatus(ID+": eroare la conexiunea server cu "+idClient);
        }
    } // end of connectToClient
    
    public void connectToServer(String ip, int port, int idServer){
        boolean connected = false;
        int max_tries = 300;
        while(connected==false){
        //for(int i=0; i<max_tries && connected==false; i++){
            try{
                //Thread.currentThread().sleep(2000);
                //Thread.sleep(2000);
                Socket s = new Socket(ip, port);
                //connected = true;
                //get the output stream
                OutputStream output = s.getOutputStream();

                //get the input stream
                InputStream input = s.getInputStream();

                //wrap the output stream in an object output stream to easily write objects
                ObjectOutputStream objectOutput = new ObjectOutputStream(output);
                objectOutput.flush();

                //add the output stream to the list fo client output streams
                this.outputstreams.add(objectOutput);

                //wrap the input stream in an object input stream to easily write objects
                ObjectInputStream objectInput = new ObjectInputStream(input);

                //add the input stream to the list of client input streams
                this.inputstreams.add(objectInput); 
                
                connected = true;
                //break;
            }
            catch (Exception e) {
                //e.printStackTrace(); 
                this.gui.updateStatus(ID+": Error connecting as client with "+idServer+" (remote server down); still trying...");     
                System.out.println(ID+": eroare la conexiunea client cu "+idServer+"; still trying...");
                try{ Thread.sleep(2000); } catch (Exception ex) {}
            }
        } // end of while
    } // end of connectToServer()
    
     
    
} // end of Communication class
class InputHandler extends Thread {
    
    Communication comm;
    ObjectInputStream ois;
    
    public InputHandler(Communication comm, ObjectInputStream ois){
        this.comm = comm;
        this.ois = ois;
    }
    
    public void run(){
        while(true){
        Message message;
        try{
            message = (Message) ois.readObject();            
            //this.comm.gui.updateStatus("received message "+message.command+message.position+message.c);
            //System.out.println("HANDLER_RUN: received message "+message.type+" towards "+message.destination+", ttl="+message.ttl);            
            this.comm.submitCommandFromPeer(message);                  
         }
         catch (Exception e) {System.out.println("HANDLER_RUN: error");}   
        }
    }
}