import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTechAdminAuth } from "@/context/TechAdminAuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2, ShieldCheck, Sun, Moon } from "lucide-react";

export function TechAdminLogin() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [theme, setTheme] = useState<"dark" | "light">("dark");

  const { login } = useTechAdminAuth();
  const navigate = useNavigate();

  const toggleTheme = () => {
    setTheme((prev) => (prev === "dark" ? "light" : "dark"));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setIsSubmitting(true);

    try {
      await login(username, password);
      navigate("/tech-admin/dashboard");
    } catch (err: any) {
      setError(err.response?.data?.message || "Invalid credentials. Access denied.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const isDark = theme === "dark";

  return (
    <div className={`relative flex min-h-screen items-center justify-center lg:justify-end lg:pr-32 transition-colors duration-300 px-4 overflow-hidden ${isDark ? "bg-slate-950 text-slate-50" : "bg-[#0a0118] text-purple-100"}`}>
      
      {/* Background Video */}
      <video 
        autoPlay 
        loop 
        muted 
        playsInline 
        className="absolute inset-0 w-full h-full object-cover"
      >
        <source src="/@fs/C:/Users/balas/Downloads/Use_the_uploaded_image_as_the (3).mp4" type="video/mp4" />
      </video>

      {/* Background Audio */}
      <audio autoPlay loop src="/@fs/C:/Users/balas/Downloads/latest puthiya manitha.mp3.mpeg" />

      {/* Overlay to ensure readability */}
      <div className={`absolute inset-0 pointer-events-none transition-colors duration-300 ${isDark ? "bg-slate-950/40" : "bg-[#0a0118]/60 backdrop-blur-[2px]"}`} />

      {/* Theme Toggle Positioned Top Right */}
      <div className="absolute top-6 right-6 z-10">
        <Button
          type="button"
          variant="outline"
          size="icon"
          onClick={toggleTheme}
          className={`rounded-full transition backdrop-blur-md ${isDark ? "bg-slate-900/40 border-cyan-500/30 text-amber-400 hover:bg-cyan-900/50" : "bg-purple-900/40 border-purple-500/30 text-amber-500 hover:bg-purple-900/60"}`}
          title={`Switch to ${isDark ? "Light" : "Dark"} Mode`}
        >
          {isDark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
        </Button>
      </div>

      <div className={`relative z-10 w-full max-w-sm space-y-6 rounded-2xl p-6 shadow-2xl transition-all duration-300 border backdrop-blur-xl ${isDark ? "bg-slate-900/40 border-cyan-500/30 shadow-[0_0_20px_rgba(6,182,212,0.15)] ring-1 ring-cyan-500/20" : "bg-[#13002b]/70 border-purple-500/30 shadow-[0_0_30px_rgba(168,85,247,0.2)] ring-1 ring-purple-500/20"}`}>
        <div className="text-center">
          <ShieldCheck className={`mx-auto h-10 w-10 drop-shadow-md ${isDark ? "text-cyan-400 drop-shadow-[0_0_8px_rgba(6,182,212,0.8)]" : "text-purple-400 drop-shadow-[0_0_10px_rgba(168,85,247,0.5)]"}`} />
          <h2 className={`mt-3 text-xl font-bold tracking-tight drop-shadow-sm ${isDark ? "text-cyan-400" : "text-purple-100"}`}>
            SaaS Control Center
          </h2>
          <p className={`mt-1.5 text-xs font-bold ${isDark ? "text-cyan-400" : "text-purple-200"}`}>
            Enterprise Technical Administration
          </p>
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          {error && (
            <div className={`rounded-md p-4 text-sm border ${isDark ? "bg-red-900/30 text-red-400 border-red-500/30 shadow-[0_0_8px_rgba(239,68,68,0.2)]" : "bg-red-900/30 text-red-400 border-red-500/30 shadow-[0_0_8px_rgba(239,68,68,0.2)]"}`}>
              {error}
            </div>
          )}

          <div className="space-y-4">
            <div>
              <Label htmlFor="username" className={isDark ? "text-cyan-100" : "text-purple-200"}>Username</Label>
              <Input
                id="username"
                type="text"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className={`mt-1 transition ${isDark ? "bg-slate-900/50 border-cyan-500/30 text-white placeholder:text-slate-500 focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400" : "bg-purple-950/50 border-purple-500/30 text-purple-100 placeholder:text-purple-400/50 focus:border-purple-400 focus:ring-1 focus:ring-purple-400"}`}
                placeholder="Admin username"
              />
            </div>

            <div>
              <Label htmlFor="password" className={isDark ? "text-cyan-100" : "text-purple-200"}>Password</Label>
              <Input
                id="password"
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={`mt-1 transition ${isDark ? "bg-slate-900/50 border-cyan-500/30 text-white placeholder:text-slate-500 focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400" : "bg-purple-950/50 border-purple-500/30 text-purple-100 placeholder:text-purple-400/50 focus:border-purple-400 focus:ring-1 focus:ring-purple-400"}`}
                placeholder="••••••••"
              />
            </div>
          </div>

          <Button
            type="submit"
            className={`w-full font-bold ${isDark ? "bg-cyan-500 hover:bg-cyan-400 text-slate-950 shadow-[0_0_15px_rgba(6,182,212,0.4)] border border-cyan-400" : "bg-purple-600 hover:bg-purple-500 text-white shadow-[0_0_15px_rgba(168,85,247,0.5)] border border-purple-400"}`}
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Authenticating...
              </>
            ) : (
              "Secure Login"
            )}
          </Button>
        </form>
      </div>
    </div>
  );
}
