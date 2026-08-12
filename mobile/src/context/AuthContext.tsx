import React, { createContext, useContext, useEffect, useState } from "react";
import { api, tokens } from "@/api/client";

interface AuthUser {
  id: number;
  name: string;
  employeeCode: string;
  roles: string[];
  permissions: string[];
}

interface AuthContextValue {
  user: AuthUser | null;
  booting: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [booting, setBooting] = useState(true);

  useEffect(() => {
    (async () => {
      const { access } = await tokens.get();
      if (access) {
        try {
          const res = await api.get("/auth/me");
          setUser(res.data.data);
        } catch {
          await tokens.clear();
        }
      }
      setBooting(false);
    })();
  }, []);

  async function login(username: string, password: string) {
    const res = await api.post("/auth/login", { username, password });
    const payload = res.data.data;
    await tokens.set(payload.tokens.accessToken, payload.tokens.refreshToken);
    setUser(payload.user);
  }

  async function logout() {
    await tokens.clear();
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, booting, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
