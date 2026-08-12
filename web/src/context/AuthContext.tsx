import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode
} from "react";
import { api, tokenStore } from "@/lib/api";
import type { AuthUser, ApiEnvelope, LoginResponse } from "@/types";

const USER_KEY = "hrp.user";

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
  hasRole: (...roles: string[]) => boolean;
  hasPermission: (...perms: string[]) => boolean;
  hasModule: (moduleCode: string) => boolean;
  /** Dashboard visibility. Unknown counts as on — see the implementation. */
  hasDashboard: () => boolean;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/// Clears the cached user written by older builds.
///
/// Nothing reads it any more, but a browser that ran a previous build still
/// holds one, and leaving it there means the next person to read this file
/// finds a key with no owner. Removing it on start also has to happen for the
/// benefit of anyone whose current screen came from that cache.
function dropLegacyUserCache() {
  try {
    localStorage.removeItem(USER_KEY);
  } catch {
    // A browser with storage disabled has nothing to drop.
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // Deliberately starting at null rather than from a cache.
  //
  // Seeding from localStorage put a signed-in-looking portal on screen before
  // anyone had confirmed the session: the name showed as "User", the sidebar
  // held only Dashboard because no permissions had loaded, and every panel sat
  // at its skeleton because the requests behind them were failing. It read as a
  // broken account rather than as no session, which is the worse of the two to
  // show someone.
  //
  // The cost is a brief loading state on every reload while /auth/me answers.
  // That is the honest thing to show while the answer is genuinely unknown.
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  // On mount: if we have a token but the stored user is stale, refresh it.
  useEffect(() => {
    let active = true;

    // A cached user is a convenience, never the thing that makes someone signed
    // in -- the token is. Trusting the cache on its own produced a signed-in
    // looking portal with no token behind it: the name blank, no permissions so
    // only Dashboard in the sidebar, and every panel empty because each request
    // was being rejected. It looked like a broken account rather than no session.
    function clearSession() {
      tokenStore.clear();
      localStorage.removeItem(USER_KEY);
      setUser(null);
    }

    async function bootstrap() {
      dropLegacyUserCache();

      if (!tokenStore.access) {
        // No token: whatever a previous build left behind is not a session.
        clearSession();
        setLoading(false);
        return;
      }

      try {
        const res = await api.get<ApiEnvelope<AuthUser>>("/auth/me");
        if (!active) return;
        if (res.data?.data) {
          setUser(res.data.data);
        } else {
          // A 200 with no user in it is not a confirmation.
          clearSession();
        }
      } catch {
        // Any failure to confirm ends the session, whether the server rejected
        // the token or could not be reached at all. The previous version kept
        // the session alive through a 5xx or a network error so a brief outage
        // would not sign everyone out — but with no user to show, what that
        // actually produced was the signed-in shell with nothing in it. Sending
        // someone to the login screen at least tells them the truth, and
        // signing in again is a smaller cost than a portal that looks broken.
        if (active) clearSession();
      } finally {
        if (active) setLoading(false);
      }
    }

    bootstrap();

    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    try {
      const res = await api.post<ApiEnvelope<LoginResponse>>("/auth/login", {
        username,
        password
      });

      const payload = res.data.data;
      tokenStore.set(payload.tokens.accessToken, payload.tokens.refreshToken);
      setUser(payload.user);
    } catch (err: any) {
      throw err;
    }
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const res = await api.get<ApiEnvelope<AuthUser>>("/auth/me");
      if (res.data?.data) {
        setUser(res.data.data);
      }
    } catch {
      // Deliberately keeping the current user here. This runs while someone is
      // already working — mid-form, mid-page — and unlike the bootstrap above
      // there is a confirmed user on screen to keep. A failed refresh means the
      // details might be a few minutes stale, not that the session is gone.
    }
  }, []);

  const logout = useCallback(() => {
    const refreshToken = tokenStore.refresh;

    if (refreshToken) {
      api.post("/auth/logout", { refreshToken }).catch(() => undefined);
    }

    tokenStore.clear();
    localStorage.removeItem(USER_KEY);
    setUser(null);
  }, []);

  const hasRole = useCallback(
    (...roles: string[]) => !!user && roles.some((r) => user.roles?.includes(r)),
    [user]
  );

  const hasPermission = useCallback(
    (...perms: string[]) => !!user && perms.some((p) => user.permissions?.includes(p)),
    [user]
  );

  const hasModule = useCallback(
    (moduleCode: string) => {
      if (!user) return false;
      const lookupId = user.companyId || (user as any).tenantId || "PIX-MASTER";
      
      const userRoles = user.roles || [];
      const isCompanyAdmin = userRoles.includes("SUPER_ADMIN") || userRoles.includes("COMPANY_ADMIN") || userRoles.includes("BOARD_ADMIN");
      const isHrManager = userRoles.includes("HR_MANAGER") || userRoles.includes("IT_HR") || userRoles.includes("CV_HR") || userRoles.includes("IT_MGR");
      const isTeamLead = userRoles.includes("TEAM_LEAD") || userRoles.includes("IT_TL") || userRoles.includes("CV_SUP");
      const isEmployee = userRoles.includes("EMPLOYEE") || userRoles.includes("IT_EMP") || userRoles.includes("CV_EMP");

      try {
        const raw = localStorage.getItem("hrp.tech_admin_company_modules");
        if (raw) {
          const data = JSON.parse(raw);
          const myCompany = data[lookupId];
          if (myCompany) {
            const mod = myCompany.find((m: any) => m.code === moduleCode);
            if (!mod) return false;
            if (!mod.enabled) return false;

            const vRoles = mod.visibleRoles || [];
            if (isCompanyAdmin && vRoles.includes("COMPANY_ADMIN")) return true;
            if (isHrManager && vRoles.includes("HR_MANAGER")) return true;
            if (isTeamLead && vRoles.includes("TEAM_LEAD")) return true;
            if (isEmployee && vRoles.includes("EMPLOYEE")) return true;
            
            return false;
          }
        }
      } catch (e) {}
      return true;
    },
    [user]
  );

  /**
   * Whether to show the dashboard.
   *
   * Separate from {@link hasModule} because the dashboard is where signing in
   * lands you. Every other module can be absent from a company's settings and
   * simply not appear; if the dashboard did that, a company that has never
   * opened the module screen would find its people signing in to nothing.
   *
   * So this hides the dashboard only when someone has explicitly switched it
   * off. Unknown means on.
   */
  const hasDashboard = useCallback(() => {
    if (!user) return false;
    const lookupId = user.companyId || (user as any).tenantId || "PIX-MASTER";
    try {
      const raw = localStorage.getItem("hrp.tech_admin_company_modules");
      if (raw) {
        const mine = JSON.parse(raw)[lookupId];
        const mod = mine?.find?.((m: any) => m.code === "DASHBOARD");
        // Present and switched off is the only case that hides it.
        if (mod && !mod.enabled) return false;
      }
    } catch {
      // Unreadable settings are not a reason to lock someone out.
    }
    return true;
  }, [user]);

  const value = useMemo(
    () => ({ user, loading, login, logout, refreshUser, hasRole, hasPermission, hasModule, hasDashboard }),
    [user, loading, login, logout, refreshUser, hasRole, hasPermission, hasModule, hasDashboard]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }

  return context;
}