package org.wildlife.Model;

public record Reporter(String name, String contact) {
    public Reporter {
        name = Checks.text(name, "Reporter name");
        contact = Checks.text(contact, "Reporter contact");
    }
}