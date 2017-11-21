package org.example.adminpanel.models;

import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.profile.CommonProfile;


public class MyUserProfile extends CommonProfile {

    private static final long serialVersionUID = 2559067845921414574L;

    public MyUserProfile() { }

    public MyUserProfile(String emailAsUsername, String role) {
        super();
        addAttribute(Pac4jConstants.USERNAME, emailAsUsername);
        addAttribute("email", emailAsUsername);
        addRole(role);
    }

}
