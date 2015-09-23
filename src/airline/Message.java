/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package airline;

import java.io.Serializable;
import java.util.ArrayList;

/**
 *
 * @author vali
 */
public class Message implements Serializable {   
    public static final int REQUEST = 0;
    public static final int REPLY = 1;
    
    int type;
    int source;
    int destination;
    int ttl;
    int via;
    int next;
    
    ArrayList<Integer> path;
    //boolean finish = false;
    
    public Message() {}
    
    public Message(int type, int source, int destination, int ttl){
        this.type = type;
        this.source = source;     
        this.destination = destination;
        this.ttl = ttl;   
        this.path = new ArrayList<Integer>();
    }
}
