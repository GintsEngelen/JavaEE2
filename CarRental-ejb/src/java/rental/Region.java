/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rental;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Region {
    
    @Id
    private String name;
    
    public Region(){
        
    }
}
