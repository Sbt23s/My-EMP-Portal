import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Play, Check, CheckCircle2, FileText, Loader2 } from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMoney, monthName } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import type { ApiEnvelope, PayrollRunResponse } from "@/types";
import { useAuth } from "@/hooks/useAuth";

export default function PayrollRunsPage() {
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  
  const [generateMonth, setGenerateMonth] = useState<number>(new Date().getMonth() + 1);
  const [generateYear, setGenerateYear] = useState<number>(new Date().getFullYear());

  const runs = useQuery({
    queryKey: ["payroll-runs"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<PayrollRunResponse[]>>("/payroll/runs")).data.data
  });

  const generateMutation = useMutation({
    mutationFn: async () => {
      return (await api.post<ApiEnvelope<PayrollRunResponse>>(`/payroll/runs?month=${generateMonth}&year=${generateYear}`)).data.data;
    },
    onSuccess: () => {
      toast.success("Payroll run generated successfully");
      queryClient.invalidateQueries({ queryKey: ["payroll-runs"] });
    },
    onError: (err) => {
      toast.error(apiMessage(err, "Failed to generate payroll run"));
    }
  });

  const confirmMutation = useMutation({
    mutationFn: async (id: number) => {
      return await api.post(`/payroll/runs/${id}/confirm`);
    },
    onSuccess: () => {
      toast.success("Payroll run confirmed");
      queryClient.invalidateQueries({ queryKey: ["payroll-runs"] });
    },
    onError: (err) => {
      toast.error(apiMessage(err, "Failed to confirm payroll run"));
    }
  });

  const approveMutation = useMutation({
    mutationFn: async (id: number) => {
      return await api.post(`/payroll/runs/${id}/finance-approve`);
    },
    onSuccess: () => {
      toast.success("Payroll run approved by Finance");
      queryClient.invalidateQueries({ queryKey: ["payroll-runs"] });
    },
    onError: (err) => {
      toast.error(apiMessage(err, "Failed to approve payroll run"));
    }
  });

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "PREVIEW":
        return <Badge variant="outline" className="text-yellow-600 border-yellow-600 bg-yellow-50">Preview</Badge>;
      case "CONFIRMED":
        return <Badge variant="outline" className="text-blue-600 border-blue-600 bg-blue-50">Confirmed</Badge>;
      case "FINANCE_APPROVED":
        return <Badge variant="outline" className="text-green-600 border-green-600 bg-green-50">Finance Approved</Badge>;
      default:
        return <Badge variant="outline">{status}</Badge>;
    }
  };

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <PageHeader title="Payroll Runs" subtitle="Manage and approve monthly payroll." />
        
        {hasPermission("PAYROLL_MANAGE") && (
          <div className="flex items-center gap-2">
            <select 
              className="px-3 py-2 border rounded-md text-sm bg-background"
              value={generateMonth}
              onChange={(e) => setGenerateMonth(parseInt(e.target.value))}
            >
              {Array.from({length: 12}).map((_, i) => (
                <option key={i+1} value={i+1}>{monthName(i+1)}</option>
              ))}
            </select>
            <input 
              type="number" 
              className="px-3 py-2 border rounded-md text-sm w-24 bg-background"
              value={generateYear}
              onChange={(e) => setGenerateYear(parseInt(e.target.value))}
            />
            <Button 
              onClick={() => generateMutation.mutate()} 
              disabled={generateMutation.isPending}
            >
              {generateMutation.isPending ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Play className="h-4 w-4 mr-2" />}
              Generate Run
            </Button>
          </div>
        )}
      </div>

      {runs.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-48" />
          ))}
        </div>
      ) : (runs.data?.length ?? 0) === 0 ? (
        <EmptyState
          icon={FileText}
          title="No payroll runs"
          description="Generate a payroll run to start processing salaries."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {runs.data!.map((run) => (
            <Card key={run.id} className="transition-shadow hover:shadow-md">
              <CardContent className="p-5">
                <div className="flex items-start justify-between mb-2">
                  <div>
                    <div className="font-display text-lg font-semibold">
                      {monthName(run.runMonth)} {run.runYear}
                    </div>
                    <div className="text-xs text-muted-foreground mt-1">
                      {run.totalEmployees} Employees Processed
                    </div>
                  </div>
                  <div>{getStatusBadge(run.status)}</div>
                </div>

                <div className="mt-4 grid grid-cols-2 gap-4">
                  <div>
                    <div className="text-xs text-muted-foreground">Total Gross</div>
                    <div className="font-semibold text-sm">{formatMoney(run.totalGross)}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">Total Net</div>
                    <div className="font-semibold text-sm text-primary">{formatMoney(run.totalNet)}</div>
                  </div>
                </div>

                <div className="mt-5 flex flex-col gap-2">
                  {run.status === "PREVIEW" && hasPermission("PAYROLL_MANAGE") && (
                    <Button 
                      variant="outline"
                      onClick={() => confirmMutation.mutate(run.id)}
                      disabled={confirmMutation.isPending}
                    >
                      {confirmMutation.isPending ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Check className="h-4 w-4 mr-2" />}
                      Confirm Run
                    </Button>
                  )}
                  {run.status === "CONFIRMED" && hasPermission("PAYROLL_APPROVE") && (
                    <Button 
                      className="bg-green-600 hover:bg-green-700 text-white"
                      onClick={() => approveMutation.mutate(run.id)}
                      disabled={approveMutation.isPending}
                    >
                      {approveMutation.isPending ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <CheckCircle2 className="h-4 w-4 mr-2" />}
                      Finance Approve
                    </Button>
                  )}
                  {run.status === "FINANCE_APPROVED" && (
                    <Button variant="secondary" disabled className="opacity-50">
                      <CheckCircle2 className="h-4 w-4 mr-2 text-green-600" />
                      Approved & Released
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
