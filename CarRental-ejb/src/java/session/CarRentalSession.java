package session;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.ejb.Stateful;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import rental.CarRentalCompany;
import rental.CarType;
import rental.Quote;
import rental.Reservation;
import rental.ReservationConstraints;
import rental.ReservationException;

@Stateful
public class CarRentalSession implements CarRentalSessionRemote {

    private String renter;
    private List<Quote> quotes = new LinkedList<Quote>();
    
    @PersistenceContext 
    EntityManager em;

    @Override
    public Set<String> getAllRentalCompanies() {
        Set<String> rentals = new HashSet<String>(em.createNamedQuery("getAllCompanies").getResultList());
        return rentals;
    }
    
    @Override
    public List<CarType> getAvailableCarTypes(Date start, Date end) {
        return em.createNamedQuery("getAvailableCarTypes")
                .setParameter("startDateInput", start)
                .setParameter("endDateInput", end)
                .getResultList();
    }

    @Override
    public List<Quote> getCurrentQuotes() {
        return quotes;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public List<Reservation> confirmQuotes() throws ReservationException {
        List<Reservation> done = new LinkedList<Reservation>();
        try {
            for (Quote quote : quotes) {
                Reservation res = em.find(CarRentalCompany.class, quote.getRentalCompany()).confirmQuote(quote);
                this.em.persist(res);
                done.add(res);  
            }
        } catch (Exception e) {
            for(Reservation r:done)
                em.find(CarRentalCompany.class, r.getRentalCompany()).cancelReservation(r);
            throw new ReservationException(e);
        }
        return done;
    }

    @Override
    public void setRenterName(String name) {
        if (renter != null) {
            throw new IllegalStateException("name already set");
        }
        renter = name;
    }

    @Override
    public String getCheapestCarType(Date start, Date end, String region) {
        List<CarRentalCompany> rentals = em.createNamedQuery("getAllCompaniesObjects").getResultList();
        
            if(region != null){
                ArrayList<CarRentalCompany> rentalsInRegion = new ArrayList<CarRentalCompany>();
        
                for(CarRentalCompany company : rentals){
                    if(company.getRegions().contains(region)) rentalsInRegion.add(company);
                }
                
                rentals = rentalsInRegion;
        }
        
        String query = 
                "SELECT car.type.name " +
                "FROM CarRentalCompany c, IN (c.cars) car " +
                "WHERE car.id NOT IN (" +
                "            SELECT r.carId " +
                "            FROM Reservation r " +
                "            WHERE (:startDateInput BETWEEN r.startDate AND r.endDate) " +
                "            OR (:endDateInput BETWEEN r.startDate AND r.endDate)" +
                "            ) AND (";
        
            boolean addOr = false;
            for(CarRentalCompany rental : rentals){
                
                if(addOr) query += " OR ";
                
                query += ("c.name LIKE \"" + rental.getName() + "\"");
                
                addOr = true;
            }
            
            if(region == null) query += " TRUE ";
            
            query += ") ORDER BY car.type.rentalPricePerDay asc"; 
            
        List<String> carTypeNames = em.createQuery(query)
                .setParameter("startDateInput", start)
                .setParameter("endDateInput", end)
                .getResultList();
        return carTypeNames.get(0);
    }

    @Override
    public void createQuote(String renter, Date start, Date end, String carType, String region) throws ReservationException {
        ReservationConstraints constraints = new ReservationConstraints(start, end, carType, region);
        try {
            List<CarRentalCompany> rentals = em.createNamedQuery("getAllCompaniesObjects").getResultList();
        
            for(CarRentalCompany company : rentals){
                if(company.getRegions().contains(region)){
                    
                    List<String> result = em.createNamedQuery("getAvailableCarTypesForCompany")
                            .setParameter("carTypeInput", carType)
                            .setParameter("startDateInput", start)
                            .setParameter("endDateInput", end)
                            .setParameter("crcNameInput", company.getName())
                            .getResultList();
                    
                    if(!result.isEmpty()) {
                        Quote out = em.find(CarRentalCompany.class, company.getName()).createQuote(constraints, renter);
                        quotes.add(out);
                        return;
                    }
                }
            }

           throw new ReservationException("No available cars found for given constraints");
        } catch(Exception e) {
            throw new ReservationException(e);
        }
    }
}