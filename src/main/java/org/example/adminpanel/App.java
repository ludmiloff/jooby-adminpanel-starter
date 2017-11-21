package org.example.adminpanel;

import org.example.adminpanel.models.MyUserProfile;
import org.jdbi.v3.core.Jdbi;
import org.jooby.*;
import org.jooby.assets.Assets;
import org.jooby.jdbc.Jdbc;
import org.jooby.jdbi.Jdbi3;
import org.jooby.pac4j.Auth;
import org.jooby.pac4j.AuthStore;
import org.jooby.pebble.Pebble;
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.util.JavaSerializationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.jooby.whoops.Whoops;

/**
 * @author jooby generator
 */
public class App extends Jooby {

    public final static Logger log = LoggerFactory.getLogger(App.class);

    {
// WHOOPS, not working on Java 9
//        /** Whoops */
//        on("dev", () -> {
//            use(new Whoops());
//        });
//        // TODO: custom error page on prod env

        /* Assets */
        use(new Assets());

        /* Template engine: */
        use(new Pebble("templates", ".html"));

        /* JDBI v3 */
        use(new Jdbc());
        use(new Jdbi3());


        onStart(() -> {
            Env env = require(Env.class);
            env.ifMode("dev", () -> {
                log.info("DEVELOPMENT MODE");
                Jdbi jdbi = require(Jdbi.class);
                DbProfileService service = require(DbProfileService.class);
                jdbi.useHandle(h -> {
                    h.createUpdate("create table users (id int auto_increment, username varchar(255), password varchar(255), serializedprofile varchar(10000))")
                            .execute();
                    MyUserProfile admin = new MyUserProfile("admin", "admin");
                    service.create(admin, "123");
                });
                return 0;
            });
        });

        before((req, rsp) -> {
            boolean loggedIn = req.session().get(Auth.ID).toOptional().isPresent();
            req.set("loggedIn", loggedIn);
        });

        get("/", req -> {
            if (req.<Boolean> get("loggedIn")) {
                MyUserProfile profile = getUserProfile(req);
                return Results.html("index")
                        .put("username", profile.getUsername())
                        .put("profile", profile);
            } else {
                return Results.html("index");
            }
        });

        get("/login", req -> Results.html("login"));

        use(new Auth()
            .form("*", DbProfileService.class)
            .authorizer("admin", "/admin/**", new RequireAnyRoleAuthorizer("admin"))
        );
        use(AdminController.class);

    }

    private MyUserProfile getUserProfile(final Request req) throws Exception {
        // direct vs indirect clients

        String profileId = req.<String> ifGet(Auth.ID).orElseGet(() -> req.session().get(Auth.ID).value(""));
        // show profile or 401
        if (profileId.isEmpty()) {
            throw new Err(Status.UNAUTHORIZED);
        }
        AuthStore store = req.require(AuthStore.class);
        if (store.get(profileId).isPresent()) {
            return (MyUserProfile) store.get(profileId).get();
        } else {
            throw new Err(Status.UNAUTHORIZED);
        }
    }

    public static void main(final String[] args) {
    run(App::new, args);
  }

}
