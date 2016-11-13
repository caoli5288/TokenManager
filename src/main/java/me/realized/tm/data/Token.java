package me.realized.tm.data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.UUID;

/**
 * Created on 16-11-14.
 */
@Entity
@Table(name = "tokenmanager")
public class Token {

    @Id
    @Column(name = "uuid")
    private UUID id;

    private String name;

    @Column(name = "tokens")
    private int amount;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

}
