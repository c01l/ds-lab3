package nameserver;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import java.io.PrintStream;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



public class RMINameserverObject extends UnicastRemoteObject implements INameserver {

    private ConcurrentHashMap<String, INameserver> nameserverHashMap;
    private ConcurrentHashMap<String, String> registeredUserHashMap;
    private PrintStream userResponseStream;

    public RMINameserverObject() throws RemoteException{
        super();
        this.nameserverHashMap = new ConcurrentHashMap<>();
        this.registeredUserHashMap = new ConcurrentHashMap<>();
    }
    public RMINameserverObject(PrintStream outputStream) throws RemoteException{
        super();
        this.nameserverHashMap = new ConcurrentHashMap<>();
        this.registeredUserHashMap = new ConcurrentHashMap<>();
        this.userResponseStream = outputStream;
    }

    /*
    returns the domains of the nameservers registered here
     */
    public String getNameservers() {
        if (this.nameserverHashMap.isEmpty())
            return "This nameserver has no children";

        StringBuilder sb = new StringBuilder();
        int counter = 1;
        for (Map.Entry<String, INameserver> ns : this.nameserverHashMap.entrySet()) {
            sb.append(counter++)
              .append("\t")
              .append(ns.getKey())
              .append("\n");
        }
        return sb.toString();
    }
    /*
    returns the addresses of users registered here
    TODO: sort users alphabetically
     */
    public String getAddresses() {
        if (this.registeredUserHashMap.isEmpty())
            return "Not a single user is registered here.";

        StringBuilder sb = new StringBuilder();
        int counter = 1;
        for (Map.Entry<String, String> user : this.registeredUserHashMap.entrySet()) {
            sb.append(counter++)
              .append("\t")
              .append(user.getKey())
              .append("\t")
              .append(user.getValue())
              .append("\n");
        }
        return sb.toString();
    }

    /*
    registers a User with his/her address.
    example:
    alice.vienna.at registers
        =>  @root nameserver: registerUser(alice.vienna.at, <ip>)
        =>  @"at" nameserver: registerUser(alice.vienna, <ip>)
        =>  @"vienna" nameserver: registerUser(alice, <ip>)
        =>  @"vienna" nameserver: alice is stored in concurrent hashmap
     */
    @Override
    public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        this.userResponseStream.println("Registering address '" + address + "' for user '" + username +"'");
        int index = username.lastIndexOf('.');
        if (index < 0) {
            if(this.registeredUserHashMap.containsKey(username)) {
                String message = "The user <" + username + "> is already registered!";
                this.userResponseStream.println(message);
                throw new AlreadyRegisteredException(message);
            }
            this.registeredUserHashMap.put(username, address);
        } else {
            String next = username.substring(index + 1);
            String rest = username.substring(0, index);

            if(this.nameserverHashMap.containsKey(next)) {
                INameserver childNs = this.nameserverHashMap.get(next);
                childNs.registerUser(rest, address);
            }
            else {
                String message = "The Nameserver <" + next +"> is unknown!";
                this.userResponseStream.println(message);
                throw new InvalidDomainException(message);
            }
        }
    }

    @Override
    public INameserverForChatserver getNameserver(String zone) throws RemoteException {
        this.userResponseStream.println("Nameserver for ’" + zone  +"’ requested by chatserver");
        if(this.nameserverHashMap.containsKey(zone)) {
            return this.nameserverHashMap.get(zone);
        } else {
            this.userResponseStream.println("The zone '" + zone + "is not registered.");
            return null;
        }
    }

    @Override
    public String lookup(String username) throws RemoteException {
        this.userResponseStream.println("Address for '" + username + "' requested by chatserver");
        if(this.registeredUserHashMap.containsKey(username))
            return this.registeredUserHashMap.get(username);
        else
            return null;
    }

    @Override
    public synchronized void registerNameserver(final String domain, final INameserver nameserver, final INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        this.userResponseStream.println("Registering nameserver for zone ’" + domain + "'");
        int index = domain.lastIndexOf('.');
        if (index < 0) {
            if(this.nameserverHashMap.containsKey(domain)) {
                String message = "The nameserver <" + domain + "> is already registered!";
                this.userResponseStream.println(message);
                throw new AlreadyRegisteredException(message);
            }
            this.nameserverHashMap.put(domain, nameserver);
        } else {
            String next = domain.substring(index + 1);
            String rest = domain.substring(0, index);

            if(this.nameserverHashMap.containsKey(next)) {
                INameserver childNs = this.nameserverHashMap.get(next);
                childNs.registerNameserver(rest, nameserver, nameserverForChatserver);
            }
            else {
                String message = "The Nameserver <" + next +"> is unknown!";
                this.userResponseStream.println(message);
                throw new InvalidDomainException(message);
            }
        }
    }
}
