import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Loader2, History, Clock, Users, Activity, Filter, Search } from "lucide-react";
import { useTechAdminAuth } from "@/context/TechAdminAuthContext";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";

// Mock data generator for company-wise usage logs based on REAL users
const generateLogsForRealUsers = (companyName: string, realUsers: any[]) => {
  if (!realUsers || realUsers.length === 0) return [];

  const modules = ["Attendance", "Chat", "Payroll", "Leave Management", "Performance Appraisals", "Core HR"];
  const actions = {
    "Attendance": ["Marked Check-in", "Marked Check-out", "Reviewed Timesheet"],
    "Chat": ["Sent a message in General", "Created a new channel", "Replied to a thread"],
    "Payroll": ["Processed monthly salary", "Downloaded Payslip", "Reviewed tax deductions"],
    "Leave Management": ["Applied for Sick Leave", "Approved Vacation Request", "Checked Leave Balance"],
    "Performance Appraisals": ["Submitted Self-Appraisal", "Reviewed Team Performance", "Set OKRs"],
    "Core HR": ["Updated Profile", "Onboarded new employee", "Downloaded Company Policy"]
  };
  
  const logs = [];
  // Generate logs randomly assigned to the REAL users
  for (let i = 1; i <= Math.min(25, realUsers.length * 4); i++) {
    const randomUser = realUsers[Math.floor(Math.random() * realUsers.length)];
    const role = randomUser.role;
    const name = randomUser.name;
    
    const module = modules[Math.floor(Math.random() * modules.length)];
    // @ts-ignore
    const actionList = actions[module];
    const action = actionList[Math.floor(Math.random() * actionList.length)];
    
    // Random duration between 5m and 4h
    const durationMins = Math.floor(Math.random() * 240) + 5;
    const hours = Math.floor(durationMins / 60);
    const mins = durationMins % 60;
    const duration = hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;

    const timestamp = new Date(Date.now() - Math.floor(Math.random() * 86400000) * i);

    logs.push({
      id: i,
      name,
      role,
      module,
      action,
      duration,
      companyName,
      timestamp: timestamp.toLocaleString(),
    });
  }
  
  // Sort by newest first
  return logs.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
};

export function TechAdminAuditLogs() {
  const { theme, currentCompany } = useTechAdminAuth();
  const isDark = theme === "dark";
  const [logs, setLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");

  useEffect(() => {
    let mounted = true;
    setLoading(true);

    /*
     * Read the audit trail from the server.
     *
     * This used to build its rows in the browser: it fetched the user list and
     * handed it to generateLogsForRealUsers(), which invented a module, an
     * action and a timestamp for each person. For companies other than Pixous
     * it did not even do that — it hard-coded "Bala Admin" and "Master Admin"
     * and read the rest out of localStorage. Every line on this page was made
     * up, and a page of invented audit records is worse than an empty one:
     * an audit log is only worth having if it is the truth.
     */
    const loadAuditLog = async () => {
      try {
        const companyDbId = currentCompany?.id;
        const url = companyDbId
          ? 
          : ;

        const res = await api.get(url);
        const rows: any[] = Array.isArray(res.data?.data) ? res.data.data : [];
        if (!mounted) return;

        setLogs(
          rows.map((r) => ({
            id: r.id,
            name: r.adminUsername || "Unknown",
            role: "TECHNICAL_ADMIN",
            module: r.entityType || "-",
            action: r.action || "-",
            // Recorded actions are instantaneous; there is no session to time.
            duration: "-",
            timestamp: r.createdAt,
            detail: [r.oldValue, r.newValue].filter(Boolean).join(" → "),
            ip: r.ipAddress
          }))
        );
      } catch (err) {
        if (!mounted) return;
        // Nothing invented in place of an answer.
        setLogs([]);
      } finally {
        if (mounted) setLoading(false);
      }
    };

    loadAuditLog();

    return () => { mounted = false; };
  }, [currentCompany]);

  const filteredLogs = logs.filter(log => {
    const matchesSearch = log.name.toLowerCase().includes(searchTerm.toLowerCase()) || 
                          log.module.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          log.action.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesRole = roleFilter === "ALL" || log.role === roleFilter;
    return matchesSearch && matchesRole;
  });

  const getRoleBadgeColor = (role: string) => {
    switch(role) {
      case 'COMPANY_ADMIN': return isDark ? 'bg-cyan-900/40 text-cyan-400 border-cyan-500/30 shadow-[0_0_8px_rgba(6,182,212,0.3)]' : 'bg-purple-600/30 text-purple-200 border-purple-500/50 shadow-[0_0_8px_rgba(168,85,247,0.3)]';
      case 'HR_MANAGER': return isDark ? 'bg-indigo-900/40 text-indigo-400 border-indigo-500/30 shadow-[0_0_8px_rgba(99,102,241,0.3)]' : 'bg-indigo-900/50 text-indigo-300 border-indigo-500/40 shadow-[0_0_8px_rgba(99,102,241,0.3)]';
      case 'TEAM_LEAD': return isDark ? 'bg-teal-900/40 text-teal-400 border-teal-500/30 shadow-[0_0_8px_rgba(20,184,166,0.3)]' : 'bg-teal-900/50 text-teal-300 border-teal-500/40 shadow-[0_0_8px_rgba(20,184,166,0.3)]';
      default: return isDark ? 'bg-emerald-900/40 text-emerald-400 border-emerald-500/30 shadow-[0_0_8px_rgba(16,185,129,0.3)]' : 'bg-emerald-900/50 text-emerald-300 border-emerald-500/40 shadow-[0_0_8px_rgba(16,185,129,0.3)]';
    }
  };

  if (loading) {
    return (
      <div className="flex p-12 justify-center">
        <Loader2 className="animate-spin text-blue-500 w-8 h-8" />
      </div>
    );
  }

  // Matched to Dashboard, Companies and Users. In light mode this was a dark
  // purple panel at 40% over a photograph, with light text on top — the image
  // came through the panel and sat behind the words.
  const cardBg = isDark
    ? "bg-slate-900/40 backdrop-blur-xl border border-cyan-500/30 shadow-[0_0_15px_rgba(6,182,212,0.15)] text-slate-100"
    : "bg-white/90 backdrop-blur-md border border-white text-slate-800 shadow-xl shadow-slate-200/50";

  return (
    <div className={`min-h-screen pb-10 ${isDark ? 'text-slate-100' : 'text-purple-100'}`}>
      <div className="max-w-[1600px] mx-auto px-6 py-6 space-y-6">
        
        {/* Header */}
        <div className="flex justify-between items-start">
          <div>
            <h2 className={`text-2xl font-bold flex items-center gap-2 ${isDark ? "text-cyan-400 drop-shadow-[0_0_8px_rgba(6,182,212,0.8)]" : "text-purple-300 drop-shadow-[0_0_10px_rgba(168,85,247,0.5)]"}`}>
              <History className={`w-6 h-6 ${isDark ? "text-cyan-400" : "text-purple-400"}`} />
              Tenant Module Usage Logs
            </h2>
            <p className={`text-sm mt-1 max-w-2xl font-medium ${isDark ? "text-cyan-400" : "text-purple-200"}`}>
              {/* Was "Real-time monitoring of…", which promised something the
                  application does not do yet. Nothing records usage, so the
                  heading was writing a cheque the empty table below could not
                  cash. */}
              How employees, HRs and Team Leads use platform modules within <span className={`font-semibold ${isDark ? "text-cyan-400" : "text-purple-400"}`}>{currentCompany?.companyName || "the selected company"}</span>, once usage tracking is switched on.
            </p>
          </div>
          
          <div className={`px-4 py-2 rounded-xl border flex items-center gap-3 ${isDark ? "bg-cyan-900/40 border-cyan-500/30 text-cyan-400 shadow-[0_0_10px_rgba(6,182,212,0.3)]" : "bg-purple-900/40 border-purple-500/30 text-purple-300 shadow-[0_0_10px_rgba(168,85,247,0.3)]"}`}>
            <Activity className="w-5 h-5" />
            <div>
              <div className="text-xs font-semibold uppercase tracking-wider">Live Monitoring</div>
              <div className="text-sm font-bold">{currentCompany?.companyName}</div>
            </div>
          </div>
        </div>

        {/* Filters */}
        <Card className={cardBg}>
          <CardContent className="p-4 flex flex-col md:flex-row gap-4 justify-between items-center">
            <div className="relative w-full md:w-96">
              <Search className="w-4 h-4 absolute left-3 top-3 text-slate-400" />
              <Input
                placeholder="Search user, module, or action..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className={`pl-9 ${isDark ? "bg-slate-900/50 border-cyan-500/30 text-white focus:border-cyan-400" : "bg-purple-950/50 border-purple-500/30 text-purple-100 placeholder:text-purple-400/50 focus:border-purple-400"}`}
              />
            </div>

            <div className="flex items-center gap-2 w-full md:w-auto">
              <span className="text-xs text-slate-400 font-medium">Filter Role:</span>
              <select
                value={roleFilter}
                onChange={(e) => setRoleFilter(e.target.value)}
                className={`p-2.5 rounded-lg text-sm font-semibold outline-none transition-all ${isDark ? "bg-slate-900/50 border-cyan-500/30 text-white focus:ring-2 focus:ring-cyan-400" : "bg-purple-950/50 border-purple-500/30 text-purple-100 focus:ring-2 focus:ring-purple-500"}`}
              >
                <option value="ALL">All Roles</option>
                <option value="COMPANY_ADMIN">System Admin</option>
                <option value="HR_MANAGER">HR Manager</option>
                <option value="TEAM_LEAD">Team Lead</option>
                <option value="EMPLOYEE">Employee</option>
              </select>
            </div>
          </CardContent>
        </Card>

        {/* Logs Table */}
        <Card className={`${cardBg} overflow-hidden`}>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className={`text-xs uppercase font-medium border-b ${isDark ? 'bg-cyan-950/40 text-cyan-400 border-cyan-500/20' : 'bg-[#1a0b2e]/60 text-purple-300 border-purple-500/20'}`}>
                <tr>
                  <th className="px-6 py-4">User & Role</th>
                  <th className="px-6 py-4">Module Used</th>
                  <th className="px-6 py-4">Specific Action</th>
                  <th className="px-6 py-4">Session Duration</th>
                  <th className="px-6 py-4">Timestamp</th>
                </tr>
              </thead>
              <tbody className={`divide-y ${isDark ? 'divide-cyan-500/10' : 'divide-purple-500/20'}`}>
                {filteredLogs.length === 0 ? (
                  <tr>
                    {/* "None for the selected filters" implied that clearing
                        them would find some. Nothing writes usage rows yet, so
                        no filter can produce any, and saying which is missing
                        beats letting someone hunt through the dropdowns. */}
                    <td colSpan={5} className="text-center py-12">
                      <p className={`font-medium ${isDark ? "text-slate-300" : "text-slate-700"}`}>
                        Usage tracking is not switched on yet
                      </p>
                      <p className={`mt-1 text-sm ${isDark ? "text-slate-500" : "text-slate-500"}`}>
                        Nothing is recording which modules people open, so there is
                        nothing to show here for any company or filter.
                      </p>
                    </td>
                  </tr>
                ) : (
                  filteredLogs.map((log) => (
                    <tr key={log.id} className={`transition-colors ${isDark ? 'hover:bg-cyan-900/20' : 'hover:bg-purple-900/20'}`}>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <div className={`w-8 h-8 rounded-full flex items-center justify-center font-bold text-xs ${isDark ? 'bg-cyan-900/40 text-cyan-400 shadow-[0_0_8px_rgba(6,182,212,0.3)]' : 'bg-purple-900/60 text-purple-300 shadow-[0_0_8px_rgba(168,85,247,0.3)]'}`}>
                            {log.name.charAt(0)}
                          </div>
                          <div>
                            <div className={`font-semibold ${isDark ? 'text-slate-200' : 'text-purple-100'}`}>{log.name}</div>
                            <div className={`text-[10px] uppercase font-bold px-1.5 py-0.5 rounded inline-block mt-1 border border-transparent ${getRoleBadgeColor(log.role)}`}>
                              {log.role.replace("_", " ")}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 font-medium">
                        <span className={`px-3 py-1 rounded-full text-xs font-semibold border ${isDark ? 'bg-slate-900/50 border-cyan-500/30 text-cyan-300' : 'bg-purple-900/50 border-purple-500/30 text-purple-200'}`}>
                          {log.module}
                        </span>
                      </td>
                      <td className={`px-6 py-4 ${isDark ? 'text-slate-400' : 'text-purple-300/80'}`}>
                        {log.action}
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1.5 font-mono text-xs">
                          <Clock className={`w-3.5 h-3.5 ${isDark ? 'text-cyan-400' : 'text-purple-400'}`} />
                          <span className={isDark ? 'text-cyan-400' : 'text-purple-400'}>{log.duration}</span>
                        </div>
                      </td>
                      <td className={`px-6 py-4 text-xs font-mono ${isDark ? 'text-slate-500' : 'text-purple-300/70'}`}>
                        {log.timestamp}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Card>

      </div>
    </div>
  );
}
