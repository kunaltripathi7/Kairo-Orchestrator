import { useEffect, useState, useMemo } from "react"
import { useParams, Link } from "react-router-dom"
import axios from "axios"
import { ReactFlow, Background, Controls, Position } from "@xyflow/react"
import type { Edge, Node } from "@xyflow/react"
import "@xyflow/react/dist/style.css"
import { ArrowLeft, CheckCircle2, Clock, PlayCircle, XCircle } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"

interface Task {
  id: string
  handlerName: string
  status: string
  sequenceNumber: number
  attemptCount: number
  payload: any
  result: any
  createdAt: string
  updatedAt: string
}

interface WorkflowDetailData {
  id: string
  name: string
  status: string
  tasks: Task[]
}

const getStatusColor = (status: string) => {
  switch (status) {
    case 'COMPLETED': return '#10b981' // emerald-500
    case 'FAILED': return '#ef4444' // red-500
    case 'IN_PROGRESS': return '#3b82f6' // blue-500
    default: return '#64748b' // slate-500
  }
}

const getStatusIcon = (status: string) => {
  switch (status) {
    case 'COMPLETED': return <CheckCircle2 className="w-4 h-4 text-emerald-500" />
    case 'FAILED': return <XCircle className="w-4 h-4 text-red-500" />
    case 'IN_PROGRESS': return <PlayCircle className="w-4 h-4 text-blue-500 animate-pulse" />
    default: return <Clock className="w-4 h-4 text-slate-500" />
  }
}

export function WorkflowDetail() {
  const { id } = useParams<{ id: string }>()
  const [workflow, setWorkflow] = useState<WorkflowDetailData | null>(null)
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null)

  const selectedTask = useMemo(() => {
    return workflow?.tasks.find(t => t.id === selectedTaskId) || null
  }, [workflow, selectedTaskId])

  useEffect(() => {
    const fetchWorkflow = async () => {
      try {
        const response = await axios.get(`/api/v1/workflows/${id}`)
        setWorkflow(response.data)
      } catch (error) {
        console.error("Failed to fetch workflow details", error)
      }
    }

    fetchWorkflow()
    const interval = setInterval(fetchWorkflow, 3000) // Fast polling for demo
    return () => clearInterval(interval)
  }, [id])

  // Convert tasks to React Flow nodes and edges
  const { nodes, edges } = useMemo(() => {
    if (!workflow || !workflow.tasks) return { nodes: [], edges: [] }

    const newNodes: Node[] = []
    const newEdges: Edge[] = []

    workflow.tasks.forEach((task, index) => {
      // Very simple horizontal layout based on sequence
      newNodes.push({
        id: task.id,
        position: { x: index * 300 + 50, y: 150 },
        data: {
          label: (
            <div className="flex flex-col items-center p-2 min-w-[150px]">
              <div className="flex items-center gap-2 mb-2">
                {getStatusIcon(task.status)}
                <span className="font-semibold">{task.handlerName}</span>
              </div>
              <span className="text-xs text-muted-foreground font-mono">
                {task.status}
              </span>
            </div>
          )
        },
        style: {
          background: '#0a0a0a',
          color: 'hsl(var(--foreground))',
          border: '1px solid rgba(255,255,255,0.1)',
          borderLeft: `4px solid ${getStatusColor(task.status)}`,
          borderRadius: '0.75rem',
          boxShadow: task.status === 'IN_PROGRESS' ? `0 0 20px ${getStatusColor(task.status)}40` : '0 4px 20px rgba(0,0,0,0.5)',
        },
        sourcePosition: Position.Right,
        targetPosition: Position.Left,
      })

      // Link to previous task (assuming linear for this simple demo)
      if (index > 0) {
        const prevTask = workflow.tasks[index - 1]
        newEdges.push({
          id: `e-${prevTask.id}-${task.id}`,
          source: prevTask.id,
          target: task.id,
          animated: prevTask.status === 'COMPLETED' && task.status === 'IN_PROGRESS',
          style: {
            stroke: prevTask.status === 'COMPLETED' ? '#10b981' : '#64748b',
            strokeWidth: 2,
          }
        })
      }
    })

    return { nodes: newNodes, edges: newEdges }
  }, [workflow])

  if (!workflow) return <div className="text-center py-20 text-muted-foreground">Loading...</div>

  return (
    <div className="space-y-6 flex flex-col h-[calc(100vh-8rem)]">
      <div className="flex items-center gap-4">
        <Link to="/" className="text-muted-foreground hover:text-foreground transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{workflow.name}</h1>
          <div className="flex items-center gap-2 mt-1">
            <span className="text-muted-foreground font-mono text-sm">{workflow.id}</span>
            <Badge variant="outline">{workflow.status}</Badge>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 flex-1 min-h-0">
        <Card className="md:col-span-1 glass-card overflow-auto relative">
          <CardHeader>
            <CardTitle>Task Execution Log</CardTitle>
            <CardDescription>Live status of all workflow tasks.</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-0 relative before:absolute before:inset-0 before:ml-[1.2rem] before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-0.5 before:bg-gradient-to-b before:from-transparent before:via-white/10 before:to-transparent">
              {workflow.tasks.map((task, index) => (
                <div 
                  key={task.id} 
                  className="relative flex items-center justify-between md:justify-normal md:odd:flex-row-reverse group is-active py-4 cursor-pointer" 
                  onClick={() => setSelectedTaskId(task.id)}
                >
                  <div className="flex items-center justify-center w-10 h-10 rounded-full border border-white/10 bg-[#0a0a0a] text-slate-500 shadow shrink-0 md:order-1 md:group-odd:-translate-x-1/2 md:group-even:translate-x-1/2 z-10">
                    {getStatusIcon(task.status)}
                  </div>
                  <div className={`w-[calc(100%-4rem)] md:w-[calc(50%-2.5rem)] p-4 rounded-xl border border-white/5 bg-white/5 backdrop-blur-sm shadow-xl transition-all hover:bg-white/10 ${selectedTaskId === task.id ? 'ring-2 ring-primary bg-white/10' : ''}`}>
                    <div className="flex items-center justify-between space-x-2 mb-1">
                      <div className="font-semibold text-foreground">{task.handlerName}</div>
                      <Badge variant="secondary" className="text-[10px] bg-black/50">Step {index + 1}</Badge>
                    </div>
                    <div className="text-xs text-muted-foreground font-mono truncate">{task.id}</div>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card className="md:col-span-2 glass-card flex flex-col">
          <CardHeader className="pb-2">
            <CardTitle>DAG Visualization</CardTitle>
          </CardHeader>
          <CardContent className="flex-1 p-0 min-h-0 relative">
            <ReactFlow
              nodes={nodes}
              edges={edges}
              fitView
              className="bg-background/20"
              colorMode="dark"
              onNodeClick={(_, node) => setSelectedTaskId(node.id)}
            >
              <Background gap={16} size={1} color="#333" variant="dots" />
              <Controls />
            </ReactFlow>
          </CardContent>
        </Card>

        {/* Task Inspector Panel (Glass Overlay) */}
        {selectedTask && (
          <div className="absolute top-0 right-0 h-full w-[450px] max-w-full glass-card border-l border-white/10 z-50 flex flex-col shadow-2xl animate-in slide-in-from-right-8 duration-300">
            <div className="p-4 border-b border-white/10 flex justify-between items-center bg-black/40">
              <div>
                <h3 className="font-semibold text-lg">{selectedTask.handlerName}</h3>
                <p className="text-xs text-muted-foreground font-mono">{selectedTask.id}</p>
              </div>
              <button onClick={() => setSelectedTaskId(null)} className="p-2 hover:bg-white/10 rounded-full transition-colors cursor-pointer">
                <XCircle className="w-5 h-5 text-muted-foreground" />
              </button>
            </div>
            
            <div className="flex-1 overflow-auto p-6 space-y-6">
              {/* Metrics Row */}
              <div className="grid grid-cols-2 gap-4">
                <div className="bg-black/50 p-3 rounded-lg border border-white/5">
                  <p className="text-xs text-muted-foreground mb-1">Status</p>
                  <div className="flex items-center gap-2">
                    {getStatusIcon(selectedTask.status)}
                    <span className="font-semibold text-sm">{selectedTask.status}</span>
                  </div>
                </div>
                <div className="bg-black/50 p-3 rounded-lg border border-white/5">
                  <p className="text-xs text-muted-foreground mb-1">Attempts</p>
                  <span className="font-semibold text-sm">{selectedTask.attemptCount}</span>
                </div>
                <div className="bg-black/50 p-3 rounded-lg border border-white/5 col-span-2">
                  <p className="text-xs text-muted-foreground mb-1">Execution Timeline</p>
                  <div className="font-mono text-xs text-muted-foreground space-y-1 mt-2">
                    <div className="flex justify-between"><span>Scheduled:</span> <span className="text-foreground">{new Date(selectedTask.createdAt).toLocaleString()}</span></div>
                    <div className="flex justify-between"><span>Updated:</span> <span className="text-foreground">{selectedTask.updatedAt ? new Date(selectedTask.updatedAt).toLocaleString() : 'Pending...'}</span></div>
                  </div>
                </div>
              </div>

              {/* Payload */}
              <div>
                <h4 className="text-sm font-medium mb-2 text-primary flex items-center gap-2">
                  <div className="w-2 h-2 rounded-full bg-emerald-500"></div> Input Payload
                </h4>
                <div className="bg-[#050505] rounded-lg border border-white/5 p-4 overflow-x-auto shadow-inner">
                  <pre className="text-[11px] leading-relaxed font-mono text-emerald-400">
                    {JSON.stringify(selectedTask.payload, null, 2) || '{}'}
                  </pre>
                </div>
              </div>

              {/* Result */}
              <div>
                <h4 className="text-sm font-medium mb-2 text-primary flex items-center gap-2">
                  <div className="w-2 h-2 rounded-full bg-blue-500"></div> Execution Result
                </h4>
                <div className="bg-[#050505] rounded-lg border border-white/5 p-4 overflow-x-auto shadow-inner">
                  <pre className="text-[11px] leading-relaxed font-mono text-blue-400">
                    {selectedTask.result ? JSON.stringify(selectedTask.result, null, 2) : '// No result recorded yet'}
                  </pre>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
