package session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import rental.Car;
import rental.CarRentalCompany;
import rental.CarType;
import rental.Reservation;

@Stateless
public class ManagerSession implements ManagerSessionRemote {
    
@PersistenceContext EntityManager em;    
    
    @Override
    public Set<CarType> getCarTypes(String company) {
        try {
            return new HashSet<CarType>(em.find(CarRentalCompany.class, company).getAllTypes());
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(ManagerSession.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    @Override
    public Set<Integer> getCarIds(String company, String type) {
        Set<Integer> out = new HashSet<Integer>();
        try {
            for(Car c: em.find(CarRentalCompany.class, company).getCars(type)){
                out.add(c.getId());
            }
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(ManagerSession.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        return out;
    }

    @Override
    public int getNumberOfReservations(String company, String type, int id) {
        try {
            return em.find(CarRentalCompany.class, company).getCar(id).getReservations().size();
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(ManagerSession.class.getName()).log(Level.SEVERE, null, ex);
            return 0;
        }
    }

    @Override
    public int getNumberOfReservations(String company, String type) {
        Set<Reservation> out = new HashSet<Reservation>();
        try {
            for(Car c: em.find(CarRentalCompany.class, company).getCars(type)){
                out.addAll(c.getReservations());
            }
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(ManagerSession.class.getName()).log(Level.SEVERE, null, ex);
            return 0;
        }
        return out.size();
    }

    @Override
    public void persistRental(String datafile) {
        // Should the csv be stored locally, or at the server side? 
        try {
            CrcData data = loadData(datafile);
            CarRentalCompany company = new CarRentalCompany(data.name, data.regions, data.cars);
            em.persist(company);
            Logger.getLogger(ManagerSession.class.getName()).log(Level.INFO, "Loaded {0} from file {1}", new Object[]{data.name, datafile});
        } catch (NumberFormatException ex) {
            Logger.getLogger(ManagerSession.class.getName()).log(Level.SEVERE, "bad file", ex);
        } catch (IOException ex) {
            Logger.getLogger(ManagerSession.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private CrcData loadData(String datafile)
            throws NumberFormatException, IOException {

        CrcData out = new CrcData();
        StringTokenizer csvReader;
        int nextuid = 0;
       
        //open file from jar
        BufferedReader in = new BufferedReader(new InputStreamReader(ManagerSession.class.getClassLoader().getResourceAsStream(datafile)));
        
        try {
            while (in.ready()) {
                String line = in.readLine();
                
                if (line.startsWith("#")) {
                    // comment -> skip					
                } else if (line.startsWith("-")) {
                    csvReader = new StringTokenizer(line.substring(1), ",");
                    out.name = csvReader.nextToken();
                    out.regions = Arrays.asList(csvReader.nextToken().split(":"));
                } else {
                    csvReader = new StringTokenizer(line, ",");
                    //create new car type from first 5 fields
                    CarType type = new CarType(csvReader.nextToken(),
                            Integer.parseInt(csvReader.nextToken()),
                            Float.parseFloat(csvReader.nextToken()),
                            Double.parseDouble(csvReader.nextToken()),
                            Boolean.parseBoolean(csvReader.nextToken()));
                    //create N new cars with given type, where N is the 5th field
                    for (int i = Integer.parseInt(csvReader.nextToken()); i > 0; i--) {
                        out.cars.add(new Car(nextuid++, type));
                    }        
                }
            } 
        } finally {
            in.close();
        }

        return out;
    }

    @Override
    public Set<String> getBestClients() {
        // JDBC Does not allow subquery in FROM clause
        Long maxReservations = (Long) em.createNamedQuery("getNumberOfReservations").getResultList().get(0);
        
        return new HashSet<String>(em.createNamedQuery("getClientsForNumberOfReservations")
                .setParameter("maxReservations", maxReservations)
                .getResultList());
    }

    @Override
    public CarType getMostPopularCarTypeIn(String carRentalCompanyName, int year) {
        Long maxReservations = (Long) em.createNamedQuery("getNumberOfReservationsForCompany")
                .setParameter("rentalCompanyInput", carRentalCompanyName)
                .setParameter("yearInput", year)
                .getResultList().get(0);
        
        String carTypeString = (String) em.createNamedQuery("getCarTypeForNumberOfReservationsForCompany")
                .setParameter("rentalCompanyInput", carRentalCompanyName)
                .setParameter("yearInput", year)
                .setParameter("maxReservations", maxReservations)
                .getResultList().get(0);
        
       int carTypeId = (int) em.createQuery("SELECT t.id "
                                              + "FROM CarRentalCompany crc, IN (crc.carTypes) t "
                                              + "WHERE crc.name LIKE :rentalCompanyInput AND t.name LIKE :carTypeInput")
               .setParameter("rentalCompanyInput", carRentalCompanyName)
               .setParameter("carTypeInput", carTypeString)
               .getResultList().get(0);
              
        return em.find(CarType.class, carTypeId);
    }

    @Override
    public int getNumberOfReservationsBy(String clientName) {
         List<Long> resultList = em.createNamedQuery("getNumberOfReservationsByClient")
                .setParameter("carRenterInput", clientName)
                .getResultList();
        
         
        if(resultList.isEmpty()) return 0;
        return Math.toIntExact(resultList.get(0));
    }

    @Override
    public int getNumberOfReservationsForCarType(String carRentalName, String carType) {
        List<Long> resultList = em.createNamedQuery("getNumberOfReservationsForCarType")
                .setParameter("rentalCompanyInput", carRentalName)
                .setParameter("carTypeInput", carType)
                .getResultList();
        
        if(resultList.isEmpty()) return 0;
        return Math.toIntExact(resultList.get(0));
    }
    
    static class CrcData {
            public List<Car> cars = new LinkedList<Car>();
            public String name;
            public List<String> regions =  new LinkedList<String>();
    }
}