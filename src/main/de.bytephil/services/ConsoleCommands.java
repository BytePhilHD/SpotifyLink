package services;


import enums.MessageType;
import main.Main;

public class ConsoleCommands {

    public void handleCommand(String command) {
        String[] commandargs = command.split("\\s+");

        if (command.toLowerCase().equalsIgnoreCase("help")) {
            Console.sendHelp();
            Console.printout("          USER HELP ", MessageType.INFO);
            Console.printout("Create new user        - user create [name] [password] [email]", MessageType.INFO);
            Console.printout("Remove user            - user delete [name]", MessageType.INFO);
            Console.printout("Get rank of user       - user getrank [name]", MessageType.INFO);
            Console.printout("Set rank of user       - user setrank [name] [rank]", MessageType.INFO);
            Console.printout("List all users         - user list", MessageType.INFO);
            Console.empty();
            Console.printout("          APPLICATION HELP ", MessageType.INFO);
            Console.printout("List all applications  - applies list", MessageType.INFO);

        } else if (commandargs[0].toLowerCase().equalsIgnoreCase("block")) {
            Main.blockedUsers.add(commandargs[1]);
            Console.printout("Added " + commandargs[1] + " to the blocked list!", MessageType.INFO);
        } else if (commandargs[0].toLowerCase().equals("unblock")) {
            Main.blockedUsers.remove(commandargs[1]);
            Console.printout("Removed " + commandargs[1] + " from the blocked list!", MessageType.INFO);
        } else {
            Console.printout("Unknown command! Type \"help\" for help!", MessageType.INFO);
        }
    }

}
