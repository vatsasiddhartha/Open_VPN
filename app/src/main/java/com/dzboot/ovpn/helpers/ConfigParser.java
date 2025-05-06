package com.dzboot.ovpn.helpers;

import android.os.Build;
import android.text.TextUtils;

import androidx.core.util.Pair;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import de.blinkt.openvpn.core.CIDRIP;
import de.blinkt.openvpn.core.Connection;
import de.blinkt.openvpn.core.VpnProfile;


public class ConfigParser {

   enum LineState {
      INITIAL,
      READING_SINGLE_QUOTE,
      READING_QUOTED,
      READING_UNQUOTED,
      DONE
   }


   public static final String CONVERTED_PROFILE = "converted Profile";
   final String[] unsupportedOptions = {"config", "tls-server"};

   // Ignore all scripts
   // in most cases these won't work and user who wish to execute scripts will
   // figure out themselves
   private final String[] ignoreOptions = {
         "tls-client",
         "allow-recursive-routing",
         "askpass",
         "auth-nocache",
         "up",
         "down",
         "route-up",
         "ipchange",
         "route-pre-down",
         "auth-user-pass-verify",
         "block-outside-dns",
         "client-cert-not-required",
         "dhcp-release",
         "dhcp-renew",
         "dh",
         "group",
         "ip-win32",
         "ifconfig-nowarn",
         "management-hold",
         "management",
         "management-client",
         "management-query-remote",
         "management-query-passwords",
         "management-query-proxy",
         "management-external-key",
         "management-forget-disconnect",
         "management-signal",
         "management-log-cache",
         "management-up-down",
         "management-client-user",
         "management-client-group",
         "pause-exit",
         "preresolve",
         "plugin",
         "machine-readable-output",
         "persist-key",
         "push",
         "register-dns",
         "route-delay",
         "route-gateway",
         "route-metric",
         "route-method",
         "status",
         "script-security",
         "show-net-up",
         "suppress-timestamps",
         "tap-sleep",
         "tmp-dir",
         "tun-ipv6",
         "topology",
         "user",
         "win-sys",
   };

   private final String[][] ignoreOptionsWithArg = {
         {"setenv", "IV_GUI_VER"},
         {"setenv", "IV_SSO"},
         {"setenv", "IV_PLAT_VER"},
         {"setenv", "IV_OPENVPN_GUI_VERSION"},
         {"engine", "dynamic"},
         {"setenv", "CLIENT_CERT"},
         {"resolv-retry", "60"}
   };

   private final String[] connectionOptions = {
         "local",
         "remote",
         "float",
         "port",
         "connect-retry",
         "connect-timeout",
         "connect-retry-max",
         "link-mtu",
         "tun-mtu",
         "tun-mtu-extra",
         "fragment",
         "mtu-disc",
         "local-port",
         "remote-port",
         "bind",
         "nobind",
         "proto",
         "http-proxy",
         "http-proxy-retry",
         "http-proxy-timeout",
         "http-proxy-option",
         "socks-proxy",
         "socks-proxy-retry",
         "http-proxy-user-pass",
         "explicit-exit-notify",
   };

   private final HashSet<String> connectionOptionsSet = new HashSet<>(Arrays.asList(connectionOptions));
   private final HashMap<String, Vector<Vector<String>>> options = new HashMap<>();
   private final HashMap<String, Vector<String>> meta = new HashMap<>();
   private String authUserPassFile;


   static public void useEmbedUserAuth(VpnProfile np, String inlineData) {
      String data = VpnProfile.getEmbeddedContent(inlineData);
      String[] parts = data.split("\n");
      if (parts.length >= 2) {
         np.mUsername = parts[0];
         np.mPassword = parts[1];
      }
   }

   static public void useEmbedHttpAuth(Connection c, String inlineData) {
      String data = VpnProfile.getEmbeddedContent(inlineData);
      String[] parts = data.split("\n");
      if (parts.length >= 2) {
         c.mProxyAuthUser = parts[0];
         c.mProxyAuthPassword = parts[1];
         c.mUseProxyAuth = true;
      }
   }

   public void parseConfig(Reader reader) throws IOException, ConfigParseError {
      HashMap<String, String> optionAliases = new HashMap<>();
      optionAliases.put("server-poll-timeout", "timeout-connect");

      BufferedReader br = new BufferedReader(reader);

      int lineno = 0;
      try {
         while (true) {
            String line = br.readLine();
            lineno++;
            if (line == null)
               break;

            if (lineno == 1) {
               if ((line.startsWith("PK\003\004")
                    || (line.startsWith("PK\007\008")))) {
                  throw new ConfigParseError(
                        "Input looks like a ZIP Archive. Import is only possible for OpenVPN config files (.ovpn/" +
                        ".conf)");
               }
               if (line.startsWith("\uFEFF")) {
                  line = line.substring(1);
               }
            }

            // Check for OpenVPN Access Server Meta information
            if (line.startsWith("# OVPN_ACCESS_SERVER_")) {
               Vector<String> metaarg = parseMeta(line);
               meta.put(metaarg.get(0), metaarg);
               continue;
            }
            Vector<String> args = parseLine(line);

            if (args.size() == 0)
               continue;


            if (args.get(0).startsWith("--"))
               args.set(0, args.get(0).substring(2));

            checkInlineFile(args, br);

            String optionname = args.get(0);
            if (optionAliases.get(optionname) != null)
               optionname = optionAliases.get(optionname);

            if (!options.containsKey(optionname)) {
               options.put(optionname, new Vector<>());
            }
            options.get(optionname).add(args);
         }
      } catch (OutOfMemoryError memoryError) {
         throw new ConfigParseError("File too large to parse: " + memoryError.getLocalizedMessage());
      }
   }

   private @NotNull Vector<String> parseMeta(@NotNull String line) {
      String meta = line.split("#\\sOVPN_ACCESS_SERVER_", 2)[1];
      String[] parts = meta.split("=", 2);
      Vector<String> rVal = new Vector<>();
      Collections.addAll(rVal, parts);
      return rVal;
   }

   private void checkInlineFile(@NotNull Vector<String> args, BufferedReader br) throws IOException, ConfigParseError {
      String arg0 = args.get(0).trim();
      // CHeck for <foo>
      if (arg0.startsWith("<") && arg0.endsWith(">")) {
         String argName = arg0.substring(1, arg0.length() - 1);
         StringBuilder inlinefile = new StringBuilder(VpnProfile.INLINE_TAG);

         String endTag = String.format("</%s>", argName);
         do {
            String line = br.readLine();
            if (line == null) {
               throw new ConfigParseError(String.format("No endTag </%s> for starttag <%s> found", argName, argName));
            }
            if (line.trim().equals(endTag))
               break;
            else {
               inlinefile.append(line);
               inlinefile.append("\n");
            }
         } while (true);

         if (inlinefile.toString().endsWith("\n"))
            inlinefile = new StringBuilder(inlinefile.substring(0, inlinefile.length() - 1));

         args.clear();
         args.add(argName);
         args.add(inlinefile.toString());
      }
   }

   public String getAuthUserPassFile() {
      return authUserPassFile;
   }

   private boolean space(char c) {
      // I really hope nobody is using zero bytes inside his/her config file
      // to sperate parameter but here we go:
      return Character.isWhitespace(c) || c == '\0';
   }

   // adapted openvpn's parse function to java
   private @NotNull Vector<String> parseLine(@NotNull String line) throws ConfigParseError {
      Vector<String> parameters = new Vector<>();

      if (line.length() == 0)
         return parameters;


      LineState state = LineState.INITIAL;
      boolean backslash = false;
      char out = 0;

      int pos = 0;
      StringBuilder currentarg = new StringBuilder();

      do {
         // Emulate the c parsing ...
         char in;
         if (pos < line.length())
            in = line.charAt(pos);
         else
            in = '\0';

         if (!backslash && in == '\\' && state != LineState.READING_SINGLE_QUOTE) {
            backslash = true;
         } else {
            if (state == LineState.INITIAL) {
               if (!space(in)) {
                  if (in == ';' || in == '#') /* comment */
                     break;
                  if (!backslash && in == '\"')
                     state = LineState.READING_QUOTED;
                  else if (!backslash && in == '\'')
                     state = LineState.READING_SINGLE_QUOTE;
                  else {
                     out = in;
                     state = LineState.READING_UNQUOTED;
                  }
               }
            } else if (state == LineState.READING_UNQUOTED) {
               if (!backslash && space(in))
                  state = LineState.DONE;
               else
                  out = in;
            } else if (state == LineState.READING_QUOTED) {
               if (!backslash && in == '\"')
                  state = LineState.DONE;
               else
                  out = in;
            } else if (state == LineState.READING_SINGLE_QUOTE) {
               if (in == '\'')
                  state = LineState.DONE;
               else
                  out = in;
            }

            if (state == LineState.DONE) {
               /* ASSERT (parm_len > 0); */
               state = LineState.INITIAL;
               parameters.add(currentarg.toString());
               currentarg = new StringBuilder();
               out = 0;
            }

            if (backslash && out != 0) {
               if (!(out == '\\' || out == '\"' || space(out))) {
                  throw new ConfigParseError("Options warning: Bad backslash ('\\') usage");
               }
            }
            backslash = false;
         }

         /* store parameter character */
         if (out != 0) {
            currentarg.append(out);
         }
      } while (pos++ < line.length());

      return parameters;
   }

   public VpnProfile convertProfile() throws ConfigParseError, IOException {
      boolean noauthtypeset = true;
      VpnProfile np = new VpnProfile();
      // Pull, client, tls-client
      np.clearDefaults();

      if (options.containsKey("client") || options.containsKey("pull")) {
         np.mUsePull = true;
         options.remove("pull");
         options.remove("client");
      }

      Vector<String> secret = getOption("secret", 1, 2);
      if (secret != null) {
         np.mAuthenticationType = VpnProfile.TYPE_STATICKEYS;
         noauthtypeset = false;
         np.mUseTLSAuth = true;
         np.mTLSAuthFilename = secret.get(1);
         if (secret.size() == 3)
            np.mTLSAuthDirection = secret.get(2);
      }

      Vector<Vector<String>> routes = getAllOption("route", 1, 4);
      if (routes != null) {
         StringBuilder routeopt = new StringBuilder();
         StringBuilder routeExcluded = new StringBuilder();
         for (Vector<String> route : routes) {
            String netmask = "255.255.255.255";
            String gateway = "vpn_gateway";

            if (route.size() >= 3)
               netmask = route.get(2);
            if (route.size() >= 4)
               gateway = route.get(3);

            String net = route.get(1);
            try {
               CIDRIP cidr = new CIDRIP(net, netmask);
               if (gateway.equals("net_gateway"))
                  routeExcluded.append(cidr.toString()).append(" ");
               else
                  routeopt.append(cidr.toString()).append(" ");
            } catch (ArrayIndexOutOfBoundsException | NumberFormatException exception) {
               throw new ConfigParseError("Could not parse netmask of route " + netmask);
            }
         }
         np.mCustomRoutes = routeopt.toString();
         np.mExcludedRoutes = routeExcluded.toString();
      }

      Vector<Vector<String>> routesV6 = getAllOption("route-ipv6", 1, 4);
      if (routesV6 != null) {
         StringBuilder customIPv6Routes = new StringBuilder();
         for (Vector<String> route : routesV6) {
            customIPv6Routes.append(route.get(1)).append(" ");
         }

         np.mCustomRoutesv6 = customIPv6Routes.toString();
      }

      Vector<String> routeNoPull = getOption("route-nopull", 0, 0);
      if (routeNoPull != null)
         np.mRoutenopull = true;

      // Also recognize tls-auth [inline] direction ...
      Vector<Vector<String>> tlsAuthOptions = getAllOption("tls-auth", 1, 2);
      if (tlsAuthOptions != null) {
         for (Vector<String> tlsAuth : tlsAuthOptions) {
            if (tlsAuth != null) {
               if (!tlsAuth.get(1).equals("[inline]")) {
                  np.mTLSAuthFilename = tlsAuth.get(1);
                  np.mUseTLSAuth = true;
               }
               if (tlsAuth.size() == 3)
                  np.mTLSAuthDirection = tlsAuth.get(2);
            }
         }
      }

      Vector<String> direction = getOption("key-direction", 1, 1);
      if (direction != null)
         np.mTLSAuthDirection = direction.get(1);

      for (String crypt : new String[]{"tls-crypt", "tls-crypt-v2"}) {
         Vector<String> tlsCrypt = getOption(crypt, 1, 1);
         if (tlsCrypt != null) {
            np.mUseTLSAuth = true;
            np.mTLSAuthFilename = tlsCrypt.get(1);
            np.mTLSAuthDirection = crypt;
         }
      }

      Vector<Vector<String>> defgw = getAllOption("redirect-gateway", 0, 7);
      if (defgw != null) {
         checkRedirectParameters(np, defgw, true);
      }

      Vector<Vector<String>> redirectPrivate = getAllOption("redirect-private", 0, 5);
      if (redirectPrivate != null) {
         checkRedirectParameters(np, redirectPrivate, false);
      }
      Vector<String> dev = getOption("dev", 1, 1);
      Vector<String> devType = getOption("dev-type", 1, 1);

      if ((devType == null || !devType.get(1).equals("tun"))
          && (dev == null || !dev.get(1).startsWith("tun"))
          && (devType != null || dev != null)) {
         throw new ConfigParseError("Sorry. Only tun mode is supported. See the FAQ for more detail");
      }

      Vector<String> mssFix = getOption("mssfix", 0, 2);

      if (mssFix != null) {
         if (mssFix.size() >= 2) {
            try {
               np.mMssFix = Integer.parseInt(mssFix.get(1));
            } catch (NumberFormatException e) {
               throw new ConfigParseError("Argument to --mssfix has to be an integer");
            }
         } else {
            np.mMssFix = 1450; // OpenVPN default size
         }
         // Ignore mtu argument of OpenVPN3 and report error otherwise
         if (mssFix.size() >= 3 && !(mssFix.get(2).equals("mtu"))) {
            throw new ConfigParseError("Second argument to --mssfix unkonwn");
         }
      }

      Vector<String> tunmtu = getOption("tun-mtu", 1, 1);

      if (tunmtu != null) {
         try {
            np.mTunMtu = Integer.parseInt(tunmtu.get(1));
         } catch (NumberFormatException e) {
            throw new ConfigParseError("Argument to --tun-mtu has to be an integer");
         }
      }

      Vector<String> mode = getOption("mode", 1, 1);
      if (mode != null) {
         if (!mode.get(1).equals("p2p"))
            throw new ConfigParseError("Invalid mode for --mode specified, need p2p");
      }

      Vector<Vector<String>> dhcpOptions = getAllOption("dhcp-option", 2, 2);
      if (dhcpOptions != null) {
         for (Vector<String> dhcpOption : dhcpOptions) {
            String type = dhcpOption.get(1);
            String arg = dhcpOption.get(2);
            if (type.equals("DOMAIN")) {
               np.mSearchDomain = dhcpOption.get(2);
            } else if (type.equals("DNS")) {
               np.mOverrideDNS = true;
               if (np.mDNS1.equals(VpnProfile.DEFAULT_DNS1))
                  np.mDNS1 = arg;
               else
                  np.mDNS2 = arg;
            }
         }
      }

      Vector<String> ifConfig = getOption("ifconfig", 2, 2);
      if (ifConfig != null) {
         try {
            CIDRIP cidr = new CIDRIP(ifConfig.get(1), ifConfig.get(2));
            np.mIPv4Address = cidr.toString();
         } catch (NumberFormatException nfe) {
            throw new ConfigParseError("Could not pase ifconfig IP address: " + nfe.getLocalizedMessage());
         }
      }

      if (getOption("remote-random-hostname", 0, 0) != null)
         np.mUseRandomHostname = true;

      if (getOption("float", 0, 0) != null)
         np.mUseFloat = true;

      if (getOption("comp-lzo", 0, 1) != null)
         np.mUseLzo = true;

      Vector<String> cipher = getOption("cipher", 1, 1);
      if (cipher != null)
         np.mCipher = cipher.get(1);

      Vector<String> auth = getOption("auth", 1, 1);
      if (auth != null)
         np.mAuth = auth.get(1);


      Vector<String> ca = getOption("ca", 1, 1);
      if (ca != null) {
         np.mCaFilename = ca.get(1);
      }

      Vector<String> cert = getOption("cert", 1, 1);
      if (cert != null) {
         np.mClientCertFilename = cert.get(1);
         np.mAuthenticationType = VpnProfile.TYPE_CERTIFICATES;
         noauthtypeset = false;
      }
      Vector<String> key = getOption("key", 1, 1);
      if (key != null)
         np.mClientKeyFilename = key.get(1);

      Vector<String> pkcs12 = getOption("pkcs12", 1, 1);
      if (pkcs12 != null) {
         np.mPKCS12Filename = pkcs12.get(1);
         np.mAuthenticationType = VpnProfile.TYPE_KEYSTORE;
         noauthtypeset = false;
      }

      Vector<String> cryptoApiCert = getOption("cryptoapicert", 1, 1);
      if (cryptoApiCert != null) {
         np.mAuthenticationType = VpnProfile.TYPE_KEYSTORE;
         noauthtypeset = false;
      }

      Vector<String> compatNames = getOption("compat-names", 1, 2);
      Vector<String> noNameRemapping = getOption("no-name-remapping", 1, 1);
      Vector<String> tlsremote = getOption("tls-remote", 1, 1);
      if (tlsremote != null) {
         np.mRemoteCN = tlsremote.get(1);
         np.mCheckRemoteCN = true;
         np.mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE;

         if ((compatNames != null && compatNames.size() > 2) ||
             (noNameRemapping != null))
            np.mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING;
      }

      Vector<String> verifyX509Name = getOption("verify-x509-name", 1, 2);
      if (verifyX509Name != null) {
         np.mRemoteCN = verifyX509Name.get(1);
         np.mCheckRemoteCN = true;
         if (verifyX509Name.size() > 2) {
            switch (verifyX509Name.get(2)) {
               case "name":
                  np.mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE_RDN;
                  break;
               case "subject":
                  np.mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE_DN;
                  break;
               case "name-prefix":
                  np.mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE_RDN_PREFIX;
                  break;
               default:
                  throw new ConfigParseError("Unknown parameter to verify-x509-name: " + verifyX509Name.get(2));
            }
         } else {
            np.mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE_DN;
         }
      }

      Vector<String> x509UsernameField = getOption("x509-username-field", 1, 1);
      if (x509UsernameField != null) {
         np.mx509UsernameField = x509UsernameField.get(1);
      }

      Vector<String> verb = getOption("verb", 1, 1);
      if (verb != null) {
         np.mVerb = verb.get(1);
      }

      if (getOption("nobind", 0, 0) != null)
         np.mNobind = true;

      if (getOption("persist-tun", 0, 0) != null)
         np.mPersistTun = true;

      if (getOption("push-peer-info", 0, 0) != null)
         np.mPushPeerInfo = true;

      Vector<String> connectRetry = getOption("connect-retry", 1, 2);
      if (connectRetry != null) {
         np.mConnectRetry = connectRetry.get(1);
         if (connectRetry.size() > 2)
            np.mConnectRetryMaxTime = connectRetry.get(2);
      }

      Vector<String> connectRetryMax = getOption("connect-retry-max", 1, 1);
      if (connectRetryMax != null)
         np.mConnectRetryMax = connectRetryMax.get(1);

      Vector<Vector<String>> remoteTLS = getAllOption("remote-cert-tls", 1, 1);
      if (remoteTLS != null)
         if (remoteTLS.get(0).get(1).equals("server"))
            np.mExpectTLSCert = true;
         else
            options.put("remotetls", remoteTLS);

      Vector<String> authUser = getOption("auth-user-pass", 0, 1);

      if (authUser != null) {
         if (noauthtypeset) {
            np.mAuthenticationType = VpnProfile.TYPE_USERPASS;
         } else if (np.mAuthenticationType == VpnProfile.TYPE_CERTIFICATES) {
            np.mAuthenticationType = VpnProfile.TYPE_USERPASS_CERTIFICATES;
         } else if (np.mAuthenticationType == VpnProfile.TYPE_KEYSTORE) {
            np.mAuthenticationType = VpnProfile.TYPE_USERPASS_KEYSTORE;
         }
         if (authUser.size() > 1) {
            if (!authUser.get(1).startsWith(VpnProfile.INLINE_TAG))
               authUserPassFile = authUser.get(1);
            np.mUsername = null;
            useEmbedUserAuth(np, authUser.get(1));
         }
      }

      Vector<String> authRetry = getOption("auth-retry", 1, 1);
      if (authRetry != null) {
         switch (authRetry.get(1)) {
            case "none":
               np.mAuthRetry = VpnProfile.AUTH_RETRY_NONE_FORGET;
               break;
            case "nointeract":
            case "interact":
               np.mAuthRetry = VpnProfile.AUTH_RETRY_NOINTERACT;
               break;
            default:
               throw new ConfigParseError("Unknown parameter to auth-retry: " + authRetry.get(2));
         }
      }

      Vector<String> crlFile = getOption("crl-verify", 1, 2);
      if (crlFile != null) {
         // If the 'dir' parameter is present just add it as custom option ..
         if (crlFile.size() == 3 && crlFile.get(2).equals("dir"))
            np.mCustomConfigOptions += join(" ", crlFile) + "\n";
         else
            // Save the filename for the config converter to add later
            np.mCrlFilename = crlFile.get(1);
      }

      Pair<Connection, Connection[]> conns = parseConnectionOptions(null);
      np.mConnections = conns.second;

      Vector<Vector<String>> connectionBlocks = getAllOption("connection", 1, 1);

      if (np.mConnections.length > 0 && connectionBlocks != null) {
         throw new ConfigParseError("Using a <connection> block and --remote is not allowed.");
      }

      if (connectionBlocks != null) {
         np.mConnections = new Connection[connectionBlocks.size()];

         int connIndex = 0;
         for (Vector<String> conn : connectionBlocks) {
            Pair<Connection, Connection[]> connectionBlockConnection =
                  parseConnection(conn.get(1), conns.first);

            if (connectionBlockConnection.second.length != 1)
               throw new ConfigParseError("A <connection> block must have exactly one remote");
            np.mConnections[connIndex] = connectionBlockConnection.second[0];
            connIndex++;
         }
      }

      if (getOption("remote-random", 0, 0) != null)
         np.mRemoteRandom = true;

      Vector<String> protoForce = getOption("proto-force", 1, 1);
      if (protoForce != null) {
         boolean disableUDP;
         String protoToDisable = protoForce.get(1);
         if (protoToDisable.equals("udp"))
            disableUDP = true;
         else if (protoToDisable.equals("tcp"))
            disableUDP = false;
         else
            throw new ConfigParseError(String.format("Unknown protocol %s in proto-force", protoToDisable));

         for (Connection conn : np.mConnections)
            if (conn.mUseUdp == disableUDP)
               conn.mEnabled = false;
      }

      // Parse OpenVPN Access Server extra
      for (String as_name_directive : new String[]{"PROFILE", "FRIENDLY_NAME"}) {
         Vector<String> friendlyName = meta.get(as_name_directive);
         if (friendlyName != null && friendlyName.size() > 1)
            np.mName = friendlyName.get(1);
      }

      Vector<String> ocusername = meta.get("USERNAME");
      if (ocusername != null && ocusername.size() > 1)
         np.mUsername = ocusername.get(1);

      checkIgnoreAndInvalidOptions(np);
      fixUp(np);

      return np;
   }

   private String join(String s, Vector<String> str) {
      if (Build.VERSION.SDK_INT > 26)
         return String.join(s, str);
      else
         return TextUtils.join(s, str);
   }

   private Pair<Connection, Connection[]> parseConnection(@NotNull String connection, Connection defaultValues)
         throws IOException, ConfigParseError {
      // Parse a connection Block as a new configuration file

      ConfigParser connectionParser = new ConfigParser();
      StringReader reader = new StringReader(connection.substring(VpnProfile.INLINE_TAG.length()));
      connectionParser.parseConfig(reader);

      return connectionParser.parseConnectionOptions(defaultValues);
   }

   private @Nullable Pair<Connection, Connection[]> parseConnectionOptions(Connection connDefault)
         throws ConfigParseError {
      Connection conn;
      if (connDefault != null)
         try {
            conn = connDefault.clone();
         } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
         }
      else
         conn = new Connection();

      Vector<String> port = getOption("port", 1, 1);
      if (port != null) {
         conn.mServerPort = port.get(1);
      }

      Vector<String> rPort = getOption("rport", 1, 1);
      if (rPort != null) {
         conn.mServerPort = rPort.get(1);
      }

      Vector<String> proto = getOption("proto", 1, 1);
      if (proto != null) {
         conn.mUseUdp = isUdpProto(proto.get(1));
      }

      Vector<String> connectTimeout = getOption("connect-timeout", 1, 1);
      if (connectTimeout != null) {
         try {
            conn.mConnectTimeout = Integer.parseInt(connectTimeout.get(1));
         } catch (NumberFormatException nfe) {
            throw new ConfigParseError(
                  String.format(
                        "Argument to connect-timeout (%s) must to be an integer: %s",
                        connectTimeout.get(1),
                        nfe.getLocalizedMessage()
                  ));
         }
      }

      Vector<String> proxy = getOption("socks-proxy", 1, 2);
      if (proxy == null)
         proxy = getOption("http-proxy", 2, 2);

      if (proxy != null) {
         if (proxy.get(0).equals("socks-proxy")) {
            conn.mProxyType = Connection.ProxyType.SOCKS5;
            // socks defaults to 1080, http always sets port
            conn.mProxyPort = "1080";
         } else {
            conn.mProxyType = Connection.ProxyType.HTTP;
         }

         conn.mProxyName = proxy.get(1);
         if (proxy.size() >= 3)
            conn.mProxyPort = proxy.get(2);
      }

      Vector<String> httpProxyAuthHTTP = getOption("http-proxy-user-pass", 1, 1);
      if (httpProxyAuthHTTP != null)
         useEmbedHttpAuth(conn, httpProxyAuthHTTP.get(1));

      // Parse remote config
      Vector<Vector<String>> remotes = getAllOption("remote", 1, 3);

      Vector<String> optionsToRemove = new Vector<>();
      // Assume that we need custom options if connectionDefault are set or in the connection specific set
      for (Map.Entry<String, Vector<Vector<String>>> option : options.entrySet()) {
         if (connDefault != null || connectionOptionsSet.contains(option.getKey())) {
            conn.mCustomConfiguration += getOptionStrings(option.getValue());
            optionsToRemove.add(option.getKey());
         }
      }

      for (String o : optionsToRemove)
         options.remove(o);

      if (!(conn.mCustomConfiguration == null || "".equals(conn.mCustomConfiguration.trim())))
         conn.mUseCustomConfig = true;

      // Make remotes empty to simplify code
      if (remotes == null)
         remotes = new Vector<>();

      Connection[] connections = new Connection[remotes.size()];


      int i = 0;
      for (Vector<String> remote : remotes) {
         try {
            connections[i] = conn.clone();
         } catch (CloneNotSupportedException e) {
            e.printStackTrace();
         }
         switch (remote.size()) {
            case 4:
               connections[i].mUseUdp = isUdpProto(remote.get(3));
            case 3:
               connections[i].mServerPort = remote.get(2);
            case 2:
               connections[i].mServerName = remote.get(1);
         }
         i++;
      }

      return Pair.create(conn, connections);
   }

   private void checkRedirectParameters(VpnProfile np, Vector<Vector<String>> defgw, boolean defaultRoute) {

      boolean noIpv4 = false;
      if (defaultRoute)

         for (Vector<String> redirect : defgw)
            for (int i = 1; i < redirect.size(); i++) {
               switch (redirect.get(i)) {
                  case "block-local":
                     np.mAllowLocalLAN = false;
                     break;
                  case "unblock-local":
                     np.mAllowLocalLAN = true;
                     break;
                  case "!ipv4":
                     noIpv4 = true;
                     break;
                  case "ipv6":
                     np.mUseDefaultRoutev6 = true;
                     break;
               }
            }
      if (defaultRoute && !noIpv4)
         np.mUseDefaultRoute = true;
   }

   private boolean isUdpProto(@NotNull String proto) throws ConfigParseError {
      boolean isudp;
      if (proto.equals("udp") || proto.equals("udp4") || proto.equals("udp6"))
         isudp = true;
      else if (proto.equals("tcp-client")
               || proto.equals("tcp")
               || proto.equals("tcp4")
               || proto.endsWith("tcp4-client")
               || proto.equals("tcp6")
               || proto.endsWith("tcp6-client"))
         isudp = false;
      else
         throw new ConfigParseError("Unsupported option to --proto " + proto);
      return isudp;
   }

   private void checkIgnoreAndInvalidOptions(VpnProfile np) throws ConfigParseError {
      for (String option : unsupportedOptions)
         if (options.containsKey(option))
            throw new ConfigParseError(String.format(
                  "Unsupported Option %s encountered in config file. Aborting",
                  option
            ));

      for (String option : ignoreOptions)
         // removing an item which is not in the map is no error
         options.remove(option);

      boolean customOptions = false;
      for (Vector<Vector<String>> option : options.values()) {
         for (Vector<String> optionsLine : option) {
            if (acknowledgeOption(optionsLine)) {
               customOptions = true;
            }
         }
      }

      if (customOptions) {
         np.mCustomConfigOptions = "# These options found in the config file do not map to config settings:\n"
                                   + np.mCustomConfigOptions;

         for (Vector<Vector<String>> option : options.values()) {
            np.mCustomConfigOptions += getOptionStrings(option);
         }
         np.mUseCustomConfig = true;
      }
   }

   boolean acknowledgeOption(Vector<String> option) {
      for (String[] ignoreOption : ignoreOptionsWithArg) {

         if (option.size() < ignoreOption.length)
            continue;

         boolean ignore = true;
         for (int i = 0; i < ignoreOption.length; i++) {
            if (!ignoreOption[i].equals(option.get(i))) {
               ignore = false;
               break;
            }
         }
         if (ignore)
            return false;
      }
      return true;
   }

   //! Generate options for custom options
   private @NotNull String getOptionStrings(@NotNull Vector<Vector<String>> option) {
      StringBuilder custom = new StringBuilder();
      for (Vector<String> optionsLine : option) {
         if (acknowledgeOption(optionsLine)) {
            // Check if option had been inlined and inline again
            if (optionsLine.size() == 2 && "extra-certs".equals(optionsLine.get(0))) {
               custom.append(VpnProfile.insertFileData(optionsLine.get(0), optionsLine.get(1)));
            } else {
               for (String arg : optionsLine)
                  custom.append(VpnProfile.openVpnEscape(arg)).append(" ");
               custom.append("\n");
            }
         }
      }
      return custom.toString();
   }

   private void fixUp(@NotNull VpnProfile np) {
      if (np.mRemoteCN.equals(np.mServerName)) {
         np.mRemoteCN = "";
      }
   }

   private @Nullable Vector<String> getOption(String option, int minArg, int maxArg) throws ConfigParseError {
      Vector<Vector<String>> allOptions = getAllOption(option, minArg, maxArg);
      if (allOptions == null)
         return null;
      else
         return allOptions.lastElement();
   }

   private @Nullable Vector<Vector<String>> getAllOption(String option, int minArg, int maxArg)
         throws ConfigParseError {
      Vector<Vector<String>> args = options.get(option);
      if (args == null)
         return null;

      for (Vector<String> optionLine : args)

         if (optionLine.size() < (minArg + 1) || optionLine.size() > maxArg + 1) {
            String err = String.format(
                  Locale.getDefault(),
                  "Option %s has %d parameters, expected between %d and %d",
                  option, optionLine.size() - 1, minArg, maxArg
            );
            throw new ConfigParseError(err);
         }
      options.remove(option);
      return args;
   }

   public static class ConfigParseError extends Exception {

      private static final long serialVersionUID = -60L;

      public ConfigParseError(String msg) {
         super(msg);
      }
   }
}




