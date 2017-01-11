package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;
import java.util.logging.LogManager;

import cli.Command;
import cli.Shell;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;

public class Nameserver implements INameserverCli, Runnable {

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;


    private Shell shell;
    private Registry registry;

    private String registryHost;
    private int registryPort;
    private String rootId;
    private String domain = "/";
    private boolean isRoot;

    private RMINameserverObject RMIObject;

    /**
     * @param componentName      the name of the component - represented in the prompt
     * @param config             the configuration to use
     * @param userRequestStream  the input stream to read user input from
     * @param userResponseStream the output stream to write the console output to
     */
    public Nameserver(String componentName, Config config,
                      InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;

        this.registryPort = this.config.getInt("registry.port");
        this.registryHost = this.config.getString("registry.host");
        this.rootId = this.config.getString("root_id");
        this.isRoot = !this.config.listKeys().contains("domain");
        if (!isRoot) this.domain = this.config.getString("domain");


        try {
            this.RMIObject = new RMINameserverObject(this.userResponseStream);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {
        if (this.isRoot) {
            // launch registry
            try {
                this.registry = LocateRegistry.createRegistry(this.registryPort);
            } catch (RemoteException e) {
                this.userResponseStream.println("Failed to create RMI Registry. Shutting down. Exception: " + e.getMessage());
                return;
            }

            // bind itself to the registry
            try {
                this.registry.rebind(this.rootId, this.RMIObject);
            } catch (RemoteException e) {
                this.userResponseStream.println("Failed to bind root Nameserver to Registry. Shutting down. Exception: " + e.getMessage());
                return;
            }
        } else {
            // register this nameserver
            try {
                this.registry = LocateRegistry.getRegistry(this.registryHost, this.registryPort);
                INameserver root = (INameserver) registry.lookup(this.rootId);
                root.registerNameserver(this.domain, this.RMIObject, this.RMIObject);
            } catch (NotBoundException e) {
                this.userResponseStream.println(e.getMessage());
            } catch (RemoteException e) {
                this.userResponseStream.println(e.getMessage());
            } catch (InvalidDomainException e) {
                this.userResponseStream.println("Domain '" + this.domain + "' is not valid ("+e.getMessage() + ")");
            } catch (AlreadyRegisteredException e) {
                this.userResponseStream.println("Nameserver '" + this.domain + "' is already registered!");
            }
        }

        shell = new Shell(componentName, this.userRequestStream, this.userResponseStream);
        shell.register(this);
        shell.run();
    }

    @Override
    @Command("!nameservers")
    public String nameservers() throws IOException {
        return this.RMIObject.getNameservers();
    }

    @Override
    @Command("!addresses")
    public String addresses() throws IOException {
        return this.RMIObject.getAddresses();
    }

    @Override
    @Command("!exit")
    public String exit() throws IOException {
        if(this.shell != null) this.shell.close();
        if (this.isRoot) {
            try {
                if(this.registry != null) this.registry.unbind(this.rootId);
            } catch (NotBoundException e) {
                e.printStackTrace();
            }
        }
        UnicastRemoteObject.unexportObject(this.RMIObject, true);
        return "Successfully shutdown nameserver.";
    }

    /**
     * @param args the first argument is the name of the {@link Nameserver}
     *             component
     */
    public static void main(String[] args) {
		LogManager.getLogManager().reset();
        String config;
        if (args.length < 1) {
            System.out.print("configname: ");
            Scanner in = new Scanner(System.in);
            config = in.nextLine();
        } else {
            config = args[0];
        }
        Nameserver nameserver = new Nameserver(config, new Config(config),
                System.in, System.out);
        Thread t = new Thread(nameserver);
        t.start();
        System.out.println("main done");
    }
}

