package nameserver;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



public class RMINameserverObject extends UnicastRemoteObject implements INameserver {

    private ConcurrentHashMap<String, INameserver> nameserverHashMap;
    private ConcurrentHashMap<String, String> registeredUserHashMap;

    public RMINameserverObject() throws RemoteException{
        super();
        this.nameserverHashMap = new ConcurrentHashMap<>();
        this.registeredUserHashMap = new ConcurrentHashMap<>();
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
        int index = username.lastIndexOf('.');
        if (index < 0) {
            if(this.registeredUserHashMap.containsKey(username)) {
                throw new AlreadyRegisteredException("The user <" + username + "> is already registered!");
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
                throw new InvalidDomainException("The Nameserver <" + next +"> is unknown!" );
            }
        }
    }

    @Override
    public INameserverForChatserver getNameserver(String zone) throws RemoteException {
        if(this.nameserverHashMap.containsKey(zone)) {
            return this.nameserverHashMap.get(zone);
        } else {
            return null;
        }
    }

    @Override
    public String lookup(String username) throws RemoteException {
        if(this.registeredUserHashMap.containsKey(username))
            return this.registeredUserHashMap.get(username);
        else
            return null;
    }

    @Override
    public synchronized void registerNameserver(final String domain, final INameserver nameserver, final INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        int index = domain.lastIndexOf('.');
        if (index < 0) {
            if(this.nameserverHashMap.containsKey(domain)) {
                throw new AlreadyRegisteredException("The nameserver <" + domain + "> is already registered!");
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
                throw new InvalidDomainException("The Nameserver <" + next +"> is unknown!" );
            }
        }
    }
}
