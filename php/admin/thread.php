<?php
/**
 * Summary of threading
 */

require_once "inc.php";

require "restricted.php";

function display_left_navigation()
{
  echo "<a href='status.php'>Summary</a><br>";
  echo "Thread<br>";

  echo "<hr>";
}

$mbeanServer = new MBeanServer();

$resin = $mbeanServer->lookup("resin:type=Resin");
$server = $mbeanServer->lookup("resin:type=Server");

$jvm_thread = $mbeanServer->lookup("java.lang:type=Threading");

$title = "Resin: Status";

if (! empty($server->Id))
  $title = $title . " for server " . $server->Id;
?>

<?php display_header("thread.php", $title) ?>

<h2>Server: <?= $server->Id ?></h2>

<?php
$thread_pool = $server->ThreadPool;
?>

<!--
"Restart" - "Exit this instance cleanly and allow the wrapper script to start a new JVM."
-->

<h2>Thread pool</h2>
<!--
<div class="description">
The ThreadPool manages all threads used by Resin.
</div>
-->

<table class="data">
  <tr>
    <th colspan='3'>Resin Threads</th>

    <th colspan='2'>JVM Threads</th>

    <th colspan='2'>Config</th>
  </tr>
  <tr>
    <th title="The number of active threads. These threads are busy servicing requests or performing other tasks.">Active</th>
    <th title="The number of idle threads. These threads are allocated but inactive, available for new requests or tasks.">Idle</th>
    <th title="The current total number of threads managed by the pool.">Total</th>

    <th title="The number of threads currently running in the JVM.">Total</th>
    <th title="The maximum number of threads running in the JVM.">Peak</th>

    <th title="The maximum number of threads that Resin can allocate.">thread-max</th>
    <th title="The minimum number of threads Resin should have available for new requests or other tasks.  This value causes a minimum number of idle threads, useful for situations where there is a sudden increase in the number of threads required.">thread-idle-min</th>
  </tr>
  <tr align='right'>
    <td><?= $thread_pool->ThreadActiveCount ?></td>
    <td><?= $thread_pool->ThreadIdleCount ?></td>
    <td><?= $thread_pool->ThreadCount ?></td>

    <td><?= $jvm_thread->ThreadCount ?></td>
    <td><?= $jvm_thread->PeakThreadCount ?></td>

    <td><?= $thread_pool->ThreadMax ?></td>
    <td><?= $thread_pool->ThreadIdleMin ?></td>
  </tr>
</table>

<?php

$threads = array();
$thread_ids = $jvm_thread->AllThreadIds;

foreach ($thread_ids as $id) {
  $threads[] = $jvm_thread->getThreadInfo($id, 50);
}

$thread_group = partition_threads($threads);
$groups = array("active", "misc", "accept", "idle");

echo "<h2>Threads</h2>\n"
echo "<table class='threads'>\n";

foreach ($groups as $name) {
  $threads = $thread_group[$name];

  if (sizeof($threads) <= 0)
    continue;

  usort($threads, "thread_name_cmp");

  echo "<tr class='head'><th colspan='4' align='left'>$name (" . sizeof($threads) . ")";

  $show = "hide('s_$name');show('h_$name');";
  foreach ($threads as $info) {
    $show .= "show('t_{$info->threadId}');";
  }

  $hide = "show('s_$name');hide('h_$name');";
  foreach ($threads as $info) {
    $hide .= "hide('t_{$info->threadId}');";
  }

  echo " <a id='s_$name' href=\"javascript:$show\">show</a> ";
  echo "<a id='h_$name' href=\"javascript:$hide\" style='display:none'>hide</a>";

  echo "</th></tr>\n";

  echo "<tr>";
  echo "<th style='border-width:0'>&nbsp;&nbsp;&nbsp;</th>";
  echo "<th>id</th>";
  echo "<th>name</th>";
  echo "<th>state</th>";
  echo "</tr>\n";

  foreach ($threads as $info) {
    echo "<tr>";

    $id = $info->threadId;

    echo "<td style='border-width:0'>&nbsp;&nbsp;&nbsp;</td>";
    echo "<td>" . $id . "</td>";
    echo "<td>" . $info->threadName . "</td>";
    echo "<td>" . $info->threadState . "</td>";

    echo "</tr>\n";

    echo "<tr id='t_$id' style='display:none' class='stack_trace'>";
    echo "<td style='border-width:0'></td>";
    echo "<td colspan='3'>";
    echo "<pre>\n";
    foreach ($info->stackTrace as $elt) {
      echo " at {$elt->className}.{$elt->methodName} ({$elt->fileName}:{$elt->lineNumber})\n";
    }
    echo "</pre>\n";
    echo "</td>";
    echo "</tr>\n";
  }
}

echo "</table>\n";

/*
foreach ($thread_ids as $id) {
  var_dump($jvm_thread->getThreadInfo($id));
}
*/
echo "</pre>";

/*

foreach ($slow_conn as $slow) {
  echo "<h3>" . $slow->ObjectName . " " . ($slow->ActiveTime / 1000) . "</h3>";

  $thread_id = $slow->ThreadId;

  resin_var_dump($thread_id);
  $info = $thread_mgr->getThreadInfo($thread_id, 16);

  if ($info) {
    $bean = Java("java.lang.management.ThreadInfo");
    $info = $bean->from($info);
  }

  resin_var_dump($info->getStackTrace());

}
*/

function thread_name_cmp($thread_a, $thread_b)
{
  if ($thread_a->threadName == $thread_b->threadName)
    return 0;
  else if ($thread_a->threadName < $thread_b->threadName)
    return -1;
  else
    return 1;
}

function partition_threads($threads)
{
  $partition = array();

  foreach ($threads as $info) {
    $stackTrace = $info->stackTrace;
    if ($stackTrace[0]->className == "java.lang.Object"
        && $stackTrace[0]->methodName == "wait"
        && $stackTrace[1]->className == "com.caucho.util.ThreadPool\$Item"
        && $stackTrace[1]->methodName == "runTasks") {
      $partition["idle"][] = $info;
    }
    else if ($stackTrace[0]->className == "com.caucho.vfs.JniServerSocketImpl"
             && $stackTrace[0]->methodName == "nativeAccept") {
      $partition["accept"][] = $info;
    }
    else if (preg_match("/^resin-tcp-connection/", $info->threadName)) {
      $partition["active"][] = $info;
    }
    else {
      $partition["misc"][] = $info;
    }
  }

  return $partition;
}

?>

<?php display_footer("status.php"); ?>
