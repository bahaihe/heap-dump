package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.Inferior;
import com.addepar.heapdump.inspect.inferior.SelfInferior;
import com.addepar.heapdump.inspect.struct.Klass;
import com.addepar.heapdump.inspect.struct.oopDesc;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author Geoff Lywood (geoff@addepar.com)
 */
public final class StatisticalHeapInspector {
  private static final int SAMPLES = 1000;

  private final PrintWriter out;
  private final Hotspot hotspot;
  private final HotspotHeap heap;
  private final int heapWordSize;
  private final Random random;

  private StatisticalHeapInspector(PrintWriter out, Hotspot hotspot) {
    this.out = out;
    this.hotspot = hotspot;
    this.heap = hotspot.getHeap();
    this.heapWordSize = hotspot.getConstants().getHeapWordSize();
    this.random = new Random();
  }

  private long getGcRunCount() {
    long gcRuns = 0;
    for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
      gcRuns += gcBean.getCollectionCount();
    }
    return gcRuns;
  }

  public void run() {
    System.gc();
    long startGcRuns = getGcRunCount();
    Graph graph = new Graph();
    List<AddressRange> liveRegions = heap.collectLiveRegions();
    OopFinder finder = new OopFinder(hotspot);

    long totalSize = 0L;

    for (AddressRange liveRegion : liveRegions) {
      long bottom = liveRegion.getStart();
      long top = liveRegion.getEnd();
      totalSize += top - bottom;
    }

    long startTime = System.currentTimeMillis();

    int totalHits = 0;
    for (int i = 0; i < SAMPLES; i++) {

      long randomOffset = randomLong(totalSize);

      for (AddressRange liveRegion : liveRegions) {
        long bottom = liveRegion.getStart();
        long top = liveRegion.getEnd();
        long size = top - bottom;
        if (size > randomOffset) {
          if (finder.probeForObject(bottom + randomOffset, bottom)) {
            addToGraph(graph, finder.getProbedObject(), finder.getProbedKlass());
            totalHits++;
          }
          break;
        } else {
          randomOffset -= size;
        }
      }
    }

    long endTime = System.currentTimeMillis();
    long endGcRuns = getGcRunCount();

    write(graph, totalSize, totalHits, endTime - startTime, endGcRuns - startGcRuns);
  }

  private void addToGraph(Graph graph, oopDesc object, Klass klass) {
    Node node = graph.nodes.get(klass.getAddress());
    if (node == null) {
      node = new Node();
      node.klassName = klass.getName(hotspot);
      graph.nodes.put(klass.getAddress(), node);
    }
    node.hits++;
    node.size += object.getObjectSize(hotspot, klass);
  }

  // This is terrible as far as random number generators go.... given that there's a 48-bit LCG
  // underlying it all, I'm not sure if this is remotely sound, even for small bounds.
  // The algorithm is more or less copied from java.util.Random.nextInt(bound)
  private long randomLong(long bound) {
    long r = random.nextLong();
    long m = bound - 1;
    long u = r;
    while (u - (r = u % bound) + m < 0) {
      u = random.nextLong();
    }
    return r;
  }

  private void write(Graph graph, long totalHeapSize, int totalHits, long millis, long gcRuns) {
    out.println();
    out.println();
    out.println("Live heap:     " + totalHeapSize);
    out.println("Total samples: " + SAMPLES);
    out.println("Total hits:    " + totalHits);
    out.println("Runtime:       " + millis + " ms");
    out.println("# GC Runs:     " + gcRuns);
    out.println();
    out.println("Hits | Estimated Number | Estimated Total Size | Class");
    out.println("------------------------------------------------------");
    List<Node> sortedNodes = new ArrayList<Node>(graph.nodes.values());
    Collections.sort(sortedNodes, new Comparator<Node>() {
      @Override
      public int compare(Node o1, Node o2) {
        return Long.signum(o2.hits - o1.hits);
      }
    });
    for (Node node : sortedNodes) {
      // ignore low fidelity stuff
      if (node.hits <= 2) {
        break;
      }
      String className = node.klassName;
      double sizeOfObject = (double) node.size / (double) node.hits;
      double estimatedSize = ((double) node.hits * (double) totalHeapSize) / (double) SAMPLES;
      double estimatedNumber = estimatedSize / sizeOfObject;
      out.format("%4d | %16.0f | %20.0f | %s\n", node.hits, estimatedNumber, estimatedSize, className);
    }
    out.flush();
  }

  public static void main(String args[]) throws IOException {
    System.in.read();
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    PrintWriter out = new PrintWriter(System.out);

    Inferior inferior = new SelfInferior();
    Hotspot hotspot = new Hotspot(inferior);
    StatisticalHeapInspector dumper = new StatisticalHeapInspector(out, hotspot);
    dumper.run();
  }

  private class Node {
    String klassName;
    long hits;
    long size;
  }

  // Represents an outgoing reference
  private class Edge {
    long destKlass;
    long hits;
  }

  private class Graph {
    Map<Long, Node> nodes = new HashMap<Long, Node>();
    Map<Node, Edge> outgoingEdges = new HashMap<Node, Edge>();
  }
}
