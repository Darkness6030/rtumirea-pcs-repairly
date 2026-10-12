package ru.rtumirea.repairly;

import ru.rtumirea.repairly.exception.RepairlyException;
import ru.rtumirea.repairly.repository.*;
import ru.rtumirea.repairly.service.*;
import ru.rtumirea.repairly.ui.*;
import ru.rtumirea.repairly.util.*;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try {
            ConnectionProvider database = DatabaseManager.fromEnvironment();
            ClientService clients = new ClientService(new ClientRepository(database));
            TechnicianService technicians =
                    new TechnicianService(new TechnicianRepository(database));
            RepairRequestService requests =
                    new RepairRequestService(
                            new RepairRequestRepository(database), clients, technicians);
            new ConsoleApplication(
                            new InputReader(new Scanner(System.in)), clients, technicians, requests)
                    .start();
        } catch (RepairlyException e) {
            System.err.println("Ошибка запуска: " + e.getMessage());
            System.exit(1);
        }
    }
}
