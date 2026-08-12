import axios from "axios";
import Constants from "expo-constants";
import * as SecureStore from "expo-secure-store";

const API_URL =
  process.env.EXPO_PUBLIC_API_URL ||
  (Constants.expoConfig?.extra as { apiUrl?: string })?.apiUrl ||
  "http://localhost:7060";

const ACCESS = "hrp.access";
const REFRESH = "hrp.refresh";

export const tokens = {
  async get() {
    return {
      access: await SecureStore.getItemAsync(ACCESS),
      refresh: await SecureStore.getItemAsync(REFRESH)
    };
  },
  async set(access: string, refresh: string) {
    await SecureStore.setItemAsync(ACCESS, access);
    await SecureStore.setItemAsync(REFRESH, refresh);
  },
  async clear() {
    await SecureStore.deleteItemAsync(ACCESS);
    await SecureStore.deleteItemAsync(REFRESH);
  }
};

export const api = axios.create({
  baseURL: `${API_URL}/api`,
  headers: { "Content-Type": "application/json" }
});

api.interceptors.request.use(async (config) => {
  const access = await SecureStore.getItemAsync(ACCESS);
  if (access) config.headers.Authorization = `Bearer ${access}`;
  return config;
});

let refreshing: Promise<string | null> | null = null;

async function doRefresh(): Promise<string | null> {
  const refresh = await SecureStore.getItemAsync(REFRESH);
  if (!refresh) return null;
  try {
    const res = await axios.post(`${API_URL}/api/auth/refresh`, { refreshToken: refresh });
    const pair = res.data?.data ?? res.data;
    if (pair?.accessToken) {
      await tokens.set(pair.accessToken, pair.refreshToken ?? refresh);
      return pair.accessToken;
    }
    return null;
  } catch {
    return null;
  }
}

api.interceptors.response.use(
  (r) => r,
  async (error) => {
    const original = error.config;
    if (error.response?.status === 401 && original && !original._retry && !original.url?.includes("/auth/")) {
      original._retry = true;
      refreshing = refreshing ?? doRefresh();
      const token = await refreshing;
      refreshing = null;
      if (token) {
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      }
      await tokens.clear();
    }
    return Promise.reject(error);
  }
);

export function apiMessage(err: any, fallback = "Something went wrong") {
  return err?.response?.data?.message || err?.message || fallback;
}
