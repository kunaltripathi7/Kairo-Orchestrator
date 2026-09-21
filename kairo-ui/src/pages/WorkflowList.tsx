import { useEffect, useState } from "react"
import { Link } from "react-router-dom"
import axios from "axios"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Activity, Clock, ChevronRight } from "lucide-react"

interface Workflow {
  id: string
  name: string
  status: string
  createdAt: string
  updatedAt: string
}

export function WorkflowList() {
  const [workflows, setWorkflows] = useState<Workflow[]>([])
  const [loading, setLoading] = useState(true)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [workflowPayload, setWorkflowPayload] = useState(`{
  "name": "E-Commerce Checkout",
  "maxRetries": 3,
  "taskTimeoutSeconds": 60,
  "tasks": [
    {
      "name": "validate",
      "handler": "validate-order",
      "payload": { "orderId": "ORD-123", "items": 2 }
    },
    {
      "name": "charge",
      "handler": "charge-payment",
      "payload": { "amount": 49.99, "currency": "USD" },
      "dependsOn": ["validate"]
    },
    {
      "name": "notify",
      "handler": "send-notification",
      "payload": { "email": "customer@example.com" },
      "dependsOn": ["charge"]
    }
  ]
}`)
  const [createLoading, setCreateLoading] = useState(false)

  const fetchWorkflows = async () => {
    try {
      const response = await axios.get('/api/v1/workflows?sort=createdAt,desc&size=20', { timeout: 5000 })
      setWorkflows(response.data?.content || [])
    } catch (error) {
      console.error("Failed to fetch workflows", error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchWorkflows()
    const interval = setInterval(fetchWorkflows, 5000)
    return () => clearInterval(interval)
  }, [])

  const handleCreate = async () => {
    try {
      setCreateLoading(true)
      await axios.post('/api/v1/workflows', JSON.parse(workflowPayload))
      setIsCreateOpen(false)
      fetchWorkflows()
    } catch (error) {
      alert("Failed to create workflow. Check console for details.")
      console.error(error)
    } finally {
      setCreateLoading(false)
    }
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <Badge className="bg-emerald-500/10 text-emerald-500 hover:bg-emerald-500/20 border-emerald-500/20">Completed</Badge>
      case 'FAILED':
        return <Badge variant="destructive">Failed</Badge>
      case 'IN_PROGRESS':
        return <Badge className="bg-blue-500/10 text-blue-500 hover:bg-blue-500/20 border-blue-500/20">In Progress</Badge>
      case 'PENDING':
      default:
        return <Badge variant="secondary" className="text-muted-foreground">Pending</Badge>
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-3xl font-bold tracking-tight mb-2">Workflows</h1>
          <p className="text-muted-foreground">Monitor and manage your Kairo orchestrations.</p>
        </div>
        <button 
          onClick={() => setIsCreateOpen(true)}
          className="bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 text-white hover:opacity-90 shadow-[0_0_20px_rgba(124,58,237,0.3)] hover:shadow-[0_0_30px_rgba(124,58,237,0.5)] transition-all h-10 px-6 py-2 inline-flex items-center justify-center rounded-lg text-sm font-semibold"
        >
          Create Workflow
        </button>
      </div>

      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-md">
          <Card className="w-full max-w-lg shadow-[0_0_50px_rgba(0,0,0,0.5)] border-white/10 bg-[#0a0a0a]">
            <CardHeader>
              <CardTitle>Create New Workflow</CardTitle>
              <CardDescription>Paste your workflow JSON payload below.</CardDescription>
            </CardHeader>
            <CardContent>
              <textarea 
                className="w-full h-64 p-3 font-mono text-sm bg-background border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
                value={workflowPayload}
                onChange={(e) => setWorkflowPayload(e.target.value)}
              />
              <div className="flex justify-end gap-3 mt-4">
                <button 
                  onClick={() => setIsCreateOpen(false)}
                  className="px-4 py-2 text-sm font-medium hover:bg-accent rounded-md"
                >
                  Cancel
                </button>
                <button 
                  onClick={handleCreate}
                  disabled={createLoading}
                  className="bg-primary text-primary-foreground hover:bg-primary/90 px-4 py-2 rounded-md text-sm font-medium disabled:opacity-50"
                >
                  {createLoading ? "Creating..." : "Submit"}
                </button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4 mb-6">
        <Card className="glass-card group hover:-translate-y-1 transition-all duration-300">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Workflows</CardTitle>
            <Activity className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{workflows.length}</div>
          </CardContent>
        </Card>
        <Card className="glass-card group hover:-translate-y-1 transition-all duration-300">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Active Now</CardTitle>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {workflows.filter(w => w.status === 'IN_PROGRESS').length}
            </div>
          </CardContent>
        </Card>
      </div>

      <Card className="glass-card">
        <CardHeader>
          <CardTitle>Recent Executions</CardTitle>
          <CardDescription>A list of the latest workflow runs.</CardDescription>
        </CardHeader>
        <CardContent>
          {loading && workflows.length === 0 ? (
            <div className="text-center py-10 text-muted-foreground">Loading workflows...</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="border-border/50">
                  <TableHead className="w-[300px]">ID</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Created At</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {workflows.map((workflow) => (
                  <TableRow key={workflow.id} className="border-border/20 transition-all hover:bg-white/5 group cursor-pointer relative" onClick={() => window.location.href = `/workflows/${workflow.id}`}>
                    <TableCell className="font-mono text-sm text-muted-foreground group-hover:text-primary transition-colors">
                      {workflow.id}
                    </TableCell>
                    <TableCell className="font-medium">{workflow.name}</TableCell>
                    <TableCell>{getStatusBadge(workflow.status)}</TableCell>
                    <TableCell className="text-right text-muted-foreground">
                      <div className="flex items-center justify-end gap-3">
                        {new Date(workflow.createdAt).toLocaleString()}
                        <ChevronRight className="w-4 h-4 opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all text-primary" />
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
                {workflows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="h-24 text-center text-muted-foreground">
                      No workflows found.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
