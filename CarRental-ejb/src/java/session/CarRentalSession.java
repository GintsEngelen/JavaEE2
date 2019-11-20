package session;

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
    public Quote createQuote(String company, ReservationConstraints constraints) throws ReservationException {
        try {
            Quote out = em.find(CarRentalCompany.class, company).createQuote(constraints, renter);
            quotes.add(out);
            return out;
        } catch(Exception e) {
            throw new ReservationException(e);
        }
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
                done.add(em.find(CarRentalCompany.class, quote.getRentalCompany()).confirmQuote(quote));
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
        return em.createNamedQuery("getCheapestCarType")
                .setParameter("startDateInput", start)
                .setParameter("endDateInput", end)
                .getResultList();
    }
}