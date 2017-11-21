package org.example.adminpanel;

import com.google.inject.Inject;
import org.example.adminpanel.models.MyUserProfile;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.credentials.password.BasicSaltedSha512PasswordEncoder;
import org.pac4j.core.credentials.password.PasswordEncoder;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.JavaSerializationHelper;

import java.io.*;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DbProfileService implements Authenticator<UsernamePasswordCredentials> {

    private final Jdbi db;

    private String usersTable = "users";


    private static final String query = "select id, username, password, serializedprofile from ";
    private static final String whereClause = " where username = ?";
    private final JavaSerializationHelper javaSerializationHelper = new JavaSerializationHelper();
    private final PasswordEncoder passwordEncoder = new BasicSaltedSha512PasswordEncoder("salt");

    @Inject
    public DbProfileService(final Jdbi db) {
        this.db = db;
    }

    @Override
    public void validate(final UsernamePasswordCredentials credentials, final WebContext context) throws HttpAction {
        if (credentials == null) {
            throwsException("No credentials");
        }
        final String username = credentials.getUsername();
        final String password = credentials.getPassword();

        if (CommonHelper.isBlank(username)) {
            // actually username === email
            App.log.info("Email cannot be blank");
            throwsException("Email cannot be blank");
        }
        if (CommonHelper.isBlank(password)) {
            App.log.info("Password cannot be blank");
            throwsException("Password cannot be blank");
        }

        final String expectedPassword = passwordEncoder.encode(password);

        try (Handle h = db.open()) {
            final Optional<Map<String, Object>> result = h.createQuery(query + usersTable + whereClause)
                    .bind(0, username)
                    .mapToMap()
                    .findFirst();
            if (!result.isPresent()) {
                App.log.info("Invalid username or password");
                throwsException("Invalid username or password");
            }

            Map<String, Object> data = result.get();

            final String userPassword = (String) data.get(Pac4jConstants.PASSWORD);
            if (CommonHelper.areNotEquals(userPassword, expectedPassword)) {
                App.log.info("Invalid username or password");
                throwsException("Invalid username or password");
            }

            final String serializedProfile = (String) data.get("serializedprofile");
            if (serializedProfile == null) {
                App.log.info("User profile corrupted (null)");
                throwsException("User profile corrupted or missing");
            }

            final MyUserProfile profile = unserializeProfile(serializedProfile);
            if (profile == null) {
                App.log.info("User profile corrupted: " + serializedProfile);
                throwsException("User profile corrupted or missing");
            }

            profile.setId(data.get("id"));
            credentials.setUserProfile(profile);
        }

    }

    public void create(final MyUserProfile profile, final String password) {
        try (Handle h = db.open()) {
            String serializedProfile = serializeProfile(profile);
            App.log.info("serialized profile: " + serializedProfile);
            String encodedPassword = passwordEncoder.encode(password);
            h.createUpdate("insert into users (username, password, serializedprofile) values (?, ?, ?)")
                    .bind(0, profile.getUsername())
                    .bind(1, encodedPassword)
                    .bind(2, serializedProfile)
                    .execute();

        }
    }

    private MyUserProfile unserializeProfile(String encoded) {
        ByteArrayInputStream stream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
        ObjectInput in = null;
        try {
            in = new ObjectInputStream(stream);
            MyUserProfile profile = new MyUserProfile();
            profile.readExternal(in);
            return profile;

        } catch (IOException | ClassNotFoundException e) {
            App.log.info(e.getMessage());
            return null;

        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                // ignore close exception
            }
        }
    }

    private String serializeProfile(MyUserProfile profile) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ObjectOutput out = null;
        try {
            out = new ObjectOutputStream(stream);
            profile.writeExternal(out);
            out.flush();
            return Base64.getEncoder().encodeToString(stream.toByteArray());

        } catch (IOException e) {
            return "";

        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                // ignore close exception
            }
        }
    }

    public String getUsersTable() {
        return usersTable;
    }

    public void setUsersTable(final String usersTable) {
        CommonHelper.assertNotBlank("usersTable", usersTable);
        this.usersTable = usersTable;
    }

    private void throwsException(final String message) {
        throw new CredentialsException(message);
    }
}
