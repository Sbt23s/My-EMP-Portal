import { View, ActivityIndicator, TouchableOpacity, Alert } from "react-native";
import { NavigationContainer } from "@react-navigation/native";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Ionicons } from "@expo/vector-icons";
import { AuthProvider, useAuth } from "@/context/AuthContext";
import LoginScreen from "@/screens/LoginScreen";
import DashboardScreen from "@/screens/DashboardScreen";
import AttendanceScreen from "@/screens/AttendanceScreen";
import LeaveScreen from "@/screens/LeaveScreen";
import PayslipScreen from "@/screens/PayslipScreen";
import { colors } from "@/theme";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } }
});

const Tab = createBottomTabNavigator();

function LogoutButton() {
  const { logout } = useAuth();
  return (
    <TouchableOpacity
      style={{ marginRight: 16 }}
      onPress={() =>
        Alert.alert("Sign out", "Sign out of Pixous HR?", [
          { text: "Cancel", style: "cancel" },
          { text: "Sign out", style: "destructive", onPress: () => logout() }
        ])
      }
    >
      <Ionicons name="log-out-outline" size={22} color={colors.ink} />
    </TouchableOpacity>
  );
}

const ICONS: Record<string, keyof typeof Ionicons.glyphMap> = {
  Home: "home-outline",
  Attendance: "time-outline",
  Leave: "calendar-outline",
  Payslips: "wallet-outline"
};

function Tabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerRight: () => <LogoutButton />,
        headerStyle: { backgroundColor: colors.card },
        headerTitleStyle: { color: colors.ink, fontWeight: "700" },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.muted,
        tabBarIcon: ({ color, size }) => (
          <Ionicons name={ICONS[route.name] ?? "ellipse-outline"} size={size} color={color} />
        )
      })}
    >
      <Tab.Screen name="Home" component={DashboardScreen} />
      <Tab.Screen name="Attendance" component={AttendanceScreen} />
      <Tab.Screen name="Leave" component={LeaveScreen} />
      <Tab.Screen name="Payslips" component={PayslipScreen} />
    </Tab.Navigator>
  );
}

function Gate() {
  const { user, booting } = useAuth();
  if (booting) {
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: colors.bg }}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }
  return user ? <Tabs /> : <LoginScreen />;
}

export default function App() {
  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <NavigationContainer>
            <StatusBar style="dark" />
            <Gate />
          </NavigationContainer>
        </AuthProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
