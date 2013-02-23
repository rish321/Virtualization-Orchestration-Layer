import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.libvirt.*;

public class Run extends HttpServlet
{
	private static final long serialVersionUID = 1L;
	static Integer id = 0;
	static ArrayList<Entry> Queue;
	static ArrayList<Job> Ready;
	static ArrayList<Host_Info> hostList;
	static ArrayList<Image_Info> imageList;
	static ArrayList<Type_Info> typeList;
	static ArrayList<String> make;
	static int init = 0;
	static int last_pm = -1;
	public static void initialize() throws IOException, LibvirtException, InterruptedException
	{
		make = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader(new File("/var/libvirt/images/make")));
		String H_Name;
		while((H_Name = br.readLine()) != null)
			make.add(H_Name);
		br.close();
		Ready = new ArrayList<Job>();
		Queue = new ArrayList<Entry>();
		int pmid = 0;
		hostList = new ArrayList<Host_Info>();
		br = new BufferedReader(new FileReader(new File(make.get(0))));
		while((H_Name = br.readLine()) != null)
		{
			FileWriter fw = new FileWriter(new File("/var/libvirt/images/host"+pmid));
			Host_Info hinfo = new Host_Info();
			hinfo.pmid = pmid;
			hinfo.name = H_Name.substring(0, H_Name.indexOf('@'));
			hinfo.ip = H_Name.substring(hinfo.name.length()+1);
			hinfo.all = H_Name;
			ArrayList<String> commands;
			commands = new ArrayList<String>();
			commands.add("ssh");
			commands.add(hinfo.all);
			commands.add("/bin/uname");
			commands.add("-r");
			ProcessBuilder pb = new ProcessBuilder(commands);
			Process p = pb.start();
			String line = "", check = "";
			BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = input.readLine()) != null)
				check = line;
			input.close();
			if(check.contains("xen"))
				hinfo.hyper = "xen";
			else
				hinfo.hyper = "qemu";
			pmid++;
			String connect;
			if(hinfo.hyper.equals("qemu"))
				connect = "qemu+ssh://"+H_Name+"/system?known_hosts=/home/"+H_Name.substring(0, H_Name.indexOf('@'))+"/.ssh/known_hosts";
			else
				connect = "xen+ssh://"+H_Name;
			Connect conn = new Connect(connect, false);
			hinfo.cpu = conn.nodeInfo().cpus;
			hinfo.free_cpu = hinfo.cpu;
			fw.write(hinfo.free_cpu+"\n");
			hinfo.ram = conn.nodeInfo().memory;
			hinfo.free_ram = hinfo.ram;
			fw.write(hinfo.free_ram+"\n");
			fw.write("0");
			fw.close();
			//hinfo.vms = new Integer(0);
			//hinfo.vmids = new ArrayList<Integer>();
			hostList.add(hinfo);
		}
		br.close();
		int vmid = 0;
		imageList = new ArrayList<Image_Info>();
		br = new BufferedReader(new FileReader(new File(make.get(1))));
		while((H_Name = br.readLine()) != null)
		{
			Image_Info added = new Image_Info();
			added.id = vmid;
			added.Location_Spec = new ArrayList<Location>();
			Location addLoc = new Location();
			addLoc.user = H_Name.substring(0, H_Name.indexOf('@'));
			addLoc.ip = H_Name.substring(addLoc.user.length()+1, H_Name.indexOf(':'));
			addLoc.Loc = "/var/libvirt/images/";
			addLoc.all = H_Name;
			addLoc.wtname = addLoc.user+"@"+addLoc.ip+":"+addLoc.Loc;
			added.Location_Spec.add(addLoc);
			added.name = H_Name.substring(H_Name.indexOf("/", H_Name.indexOf("images"))+1);
			imageList.add(added);
			vmid++;
		}
		br.close();
		typeList = new ArrayList<Type_Info>();
		br = new BufferedReader(new FileReader(new File(make.get(2))));
		br.readLine();
		while((H_Name = br.readLine()) != null)
		{
			if(H_Name.equals("        {"))
			{
				Type_Info added = new Type_Info();
				String node_info = br.readLine();
				added.tid = Integer.parseInt(node_info.substring((node_info.indexOf(':') + 2), node_info.indexOf(',')));
				node_info = br.readLine();
				added.cpu = Integer.parseInt(node_info.substring((node_info.indexOf(':') + 2), node_info.indexOf(',')));
				node_info = br.readLine();
				added.ram = Integer.parseInt(node_info.substring((node_info.indexOf(':') + 2), node_info.indexOf(',')))*1024;
				node_info = br.readLine();
				added.disk = Integer.parseInt(node_info.substring((node_info.indexOf(':') + 2)));
				typeList.add(added);
			}
		}
		br.close();
		init++;
	}
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		if(init == 0)
			try
		{
				initialize();
		}
		catch (LibvirtException e1)
		{
			e1.printStackTrace();
		}
		catch (InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		response.setContentType("application/json");
		String file = request.getRequestURI()+"?"+request.getQueryString();
		if(file.contains("Virtual_Machine_Manager"))
			file = file.substring(24);
		if(file.contains("null"))
			file = file.substring(0, file.length()-5);
		JSONObject result = new JSONObject();
		try
		{
			result = main(file);
			response.getWriter().println(result);			
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		catch (LibvirtException e)
		{
			e.printStackTrace();
		}
	}
	public static JSONObject main(String input) throws IOException, InterruptedException, LibvirtException
	{
		JSONObject result = new JSONObject();
		if(input.length() < 2)
		{
			result.put("", "");
			return result;
		}
		String crux = input.substring(1);
		String command = crux.substring(0, crux.indexOf('/'));
		crux = crux.substring(command.length()+1);
		if(command.equals("vm"))
		{
			if(crux.startsWith("create"))
			{
				String vm_name = crux.substring(12, crux.indexOf('&'));
				String type = crux.substring(12+vm_name.length()+9, crux.indexOf('&', 12+vm_name.length()+2));
				int image = Integer.parseInt(crux.substring(12+vm_name.length()+9+type.length()+12));
				result = vm_create(vm_name, Integer.parseInt(type), image);
			}
			else if(crux.startsWith("query"))
			{
				int fid = Integer.parseInt(crux.substring(11));
				result = vm_query(fid);
			}
			else if(crux.startsWith("destroy"))
			{
				int fid = Integer.parseInt(crux.substring(13));
				result = vm_destroy(fid);
			}
			else if(crux.equals("types"))
				result = vm_types();
			else
				result.put("Error", "Input Error");
		}
		else if(command.equals("pm"))
		{
			if(crux.equals("list"))
				result = list_pms();
			else if(crux.endsWith("listvms"))
				result = list_vms(Integer.parseInt(crux.substring(0, crux.indexOf('/'))));
			else if(Character.isDigit(crux.charAt(0)) && Character.isDigit(crux.charAt(crux.length()-1)))
				result = pm_query(Integer.parseInt(crux));
			else
				result.put("Error", "Input Error");
		}
		else if(command.equals("image"))
		{
			if(crux.equals("list"))
				result = list_images();
			else
				result.put("Error", "Input Error");
		}
		else
			result.put("Error", "Input Error");
		return result;
	}
	private static JSONObject vm_create(String vm_name, int type, int image) throws IOException, LibvirtException
	{
		JSONObject obj = new JSONObject();
		int i;
		if(imageList.size() <= image)
		{
			obj.put("Error", "invalid Image");
			return obj;
		}
		if(typeList.size() <= type)
		{
			obj.put("Error", "invalid Type");
			return obj;
		}
		String image_loc = "";
		//bin pack algo
		if(make.get(3).equals("1"))
		{
			for(i =0; i < hostList.size(); i++)
				if(hostList.get(i).free_cpu >= typeList.get(type).cpu && hostList.get(i).free_ram >= typeList.get(type).ram)
				{
					hostList.get(i).free_cpu-=typeList.get(type).cpu;
					hostList.get(i).free_ram-=typeList.get(type).ram;
					File fl = new File("/var/libvirt/images/host"+i);
					fl.delete();
					FileWriter fw = new FileWriter(new File("/var/libvirt/images/host"+i));
					fw.write(hostList.get(i).free_cpu+"\n");
					fw.write(hostList.get(i).free_ram+"\n");
					fw.write(Integer.toString(hostList.get(i).vms+1));
					fw.close();
					break;
				}
			if(i == hostList.size())
			{
				Entry here = new Entry();
				here.image = image;
				here.type = type;
				here.name = vm_name;
				Queue.add(here);
				obj.put("Error", "Queued");
				return obj;
			}
		}
		//round robin algo
		else
		{
			int no = 0;
			for(i = (last_pm+1)%hostList.size(); no != hostList.size(); i=(i+1)%hostList.size())
			{
				if(hostList.get(i).free_cpu >= typeList.get(type).cpu && hostList.get(i).free_ram >= typeList.get(type).ram)
				{
					hostList.get(i).free_cpu-=typeList.get(type).cpu;
					hostList.get(i).free_ram-=typeList.get(type).ram;
					File fl = new File("/var/libvirt/images/host"+i);
					fl.delete();
					FileWriter fw = new FileWriter(fl);
					fw.write(hostList.get(i).free_cpu+"\n");
					fw.write(hostList.get(i).free_ram+"\n");
					fw.write(Integer.toString(hostList.get(i).vms + 1));
					fw.close();
					//hostList.get(i).free_disk-=typeList.get(type).disk;
					last_pm = i;
					break;
				}
				no++;
			}
			if(no == hostList.size())
			{
				Entry here = new Entry();
				here.image = image;
				here.type = type;
				here.name = vm_name;
				obj.put("Error", "Queued");
				return obj;
			}
		}
		int j;
		for(j = 0; j < imageList.get(image).Location_Spec.size(); j++)
			if((imageList.get(image).Location_Spec.get(j).user+"@"+imageList.get(image).Location_Spec.get(j).ip).equals(hostList.get(i).all))
			{
				image_loc = imageList.get(image).Location_Spec.get(j).Loc+imageList.get(image).name;
				break;
			}
		ArrayList<String> commands = new ArrayList<String>();
		if(j == imageList.get(image).Location_Spec.size())
		{
			commands.add("ssh");
			commands.add(hostList.get(i).all);
			commands.add("/bin/ls");
			commands.add("/var/libvirt/images/");
			ProcessBuilder pb = new ProcessBuilder(commands);
			Process p = pb.start();
			String line = "";
			BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = input.readLine()) != null)
				if(line.equals(imageList.get(image).name))
					break;
			input.close();
			if(line == null || !line.equals(imageList.get(image).name))
			{
				commands = new ArrayList<String>();
				commands.add("scp");
				commands.add(imageList.get(image).Location_Spec.get(0).all);
				commands.add(hostList.get(i).all + ":" + "/var/libvirt/images/");
				pb = new ProcessBuilder(commands);
				pb.start();
			}
			image_loc = "/var/libvirt/images/"+imageList.get(image).name;
			Location Locadd = new Location();
			Locadd.ip = hostList.get(i).ip;
			Locadd.user = hostList.get(i).name;
			Locadd.all = image_loc;
			Locadd.Loc = "/var/libvirt/images/";
			Locadd.wtname = Locadd.user + "@" + Locadd.ip + ":" + Locadd.Loc;
			imageList.get(image).Location_Spec.add(Locadd);
		}
		String hyper = "";
		hyper= hostList.get(i).hyper;
		String xml = makeconfi(hyper, vm_name, typeList.get(type).ram, typeList.get(type).cpu, image_loc);
		String connect;
		if(hyper.equals("qemu"))
			connect = "qemu+ssh://"+hostList.get(i).all+"/system?known_hosts=/home/"+hostList.get(i).name+"/.ssh/known_hosts";
		else
			connect = "xen+ssh://"+hostList.get(i).all;
		Connect conn = new Connect(connect, false);
		conn.domainCreateXML(xml, 0);
		hostList.get(i).vms++;
		Job here = new Job();
		here.pmid = i;
		here.vmid = id++;
		here.vm_name = vm_name;
		here.instance_type = type;
		Ready.add(here);
		FileWriter fw = new FileWriter(new File("/var/libvirt/images/Ready"+here.vmid));
		fw.write(Integer.toString(i));
		fw.close();
		obj.put("vmid", here.vmid);
		return obj;
	}
	private static String makeconfi(String hyper, String vm_name, int ram, int cpu, String image_loc)
	{
		String xml = "";
		xml+="<domain type='" + hyper + "'>";
		xml+="  <name>"+vm_name+"</name>\n";
		xml+="  <memory>"+ram+"</memory>\n";
		xml+="  <vcpu>"+cpu+"</vcpu>\n";
		xml+="<os>\n    <type arch='x86_64' machine='pc'>hvm</type>\n    <boot dev='hd'/>\n  </os>\n \n  <on_poweroff>destroy</on_poweroff>\n  <on_reboot>restart</on_reboot>\n  <on_crash>restart</on_crash>\n  <devices>\n    <emulator>/usr/bin/qemu-system-x86_64</emulator>\n    <disk type='file' device='disk'>\n";
		xml+="      <driver name='" + hyper + "' type='raw'/>";
		xml+="      <source file='"+image_loc+"'/>\n";
		xml+="      <target dev='hda' bus='ide'/>\n      <address type='drive' controller='0' bus='0' unit='0'/>\n    </disk>\n   </devices>\n</domain>";
		return xml;
	}
	private static JSONObject list_images() {
		JSONObject obj = new JSONObject();
		ArrayList<JSONObject> list = new ArrayList<JSONObject>();
		for(int i = 0; i < imageList.size(); i++)
		{
			JSONObject id_obj = new JSONObject();
			id_obj.put("id", imageList.get(i).id);
			id_obj.put("name", imageList.get(i).name);
			list.add(id_obj);
		}
		obj.put("images", list);
		return obj;
	}
	private static JSONObject pm_query(int pmid) throws IOException {
		JSONObject obj = new JSONObject();
		for(int i = 0; i < hostList.size(); i++)
			if(hostList.get(i).pmid == pmid)
			{
				BufferedReader br = new BufferedReader(new FileReader(new File("/var/libvirt/images/host"+pmid)));
				hostList.get(i).free_cpu = Integer.parseInt(br.readLine());
				hostList.get(i).free_ram = Integer.parseInt(br.readLine());
				hostList.get(i).vms = Integer.parseInt(br.readLine());
				br.close();
				obj.put("pmid", pmid);
				JSONObject cap_obj = new JSONObject();
				cap_obj.put("cpu", hostList.get(i).cpu);
				cap_obj.put("ram", (hostList.get(i).ram/1024));
				cap_obj.put("disk", hostList.get(i).disk);
				obj.put("capacity", cap_obj);
				JSONObject free_obj = new JSONObject();
				free_obj.put("cpu", hostList.get(i).free_cpu);
				free_obj.put("ram", (hostList.get(i).free_ram/1024));
				free_obj.put("disk", hostList.get(i).free_disk);
				obj.put("free", free_obj);
				return obj;
			}
		obj.put("Error", "Invalid pmid");
		return obj;
	}
	private static JSONObject list_vms(int pmid) throws IOException
	{
		JSONObject obj = new JSONObject();
		int j;
		for(j = 0; j < hostList.size(); j++)
			if(hostList.get(j).pmid == pmid)
				break;
		if(j < hostList.size())
		{
			ArrayList<Integer> list = new ArrayList<Integer>();
			File fl = new File("/var/libvirt/images/");
			String child[] = fl.list();
			for(int i = 0; i < child.length; i++)
			{
				if(child[i].startsWith("Ready"))
				{	
					BufferedReader br = new BufferedReader(new FileReader(new File("/var/libvirt/images/"+child[i])));
					if(Integer.parseInt(br.readLine()) == pmid)
						list.add(Integer.parseInt(child[i].substring(5)));
					br.close();
				}
			}
			obj.put("vmids", list);
			return obj;
		}
		else
		{
			obj.put("Error", "Invalid pmid");
			return obj;
		}
	}
	private static JSONObject list_pms()
	{
		JSONObject obj = new JSONObject();
		ArrayList<Integer> list = new ArrayList<Integer>();
		for(int i = 0; i < hostList.size(); i++)
			list.add(hostList.get(i).pmid);
		obj.put("pmids", list);
		return obj;
	}
	private static JSONObject vm_types() {
		JSONObject obj = new JSONObject();
		ArrayList<JSONObject> ArrObj = new ArrayList<JSONObject>();
		for(int i = 0; i < typeList.size(); i++)
		{
			JSONObject temp = new JSONObject();
			temp.put("tid", typeList.get(i).tid);
			temp.put("cpu", typeList.get(i).cpu);
			temp.put("ram", (typeList.get(i).ram/1024));
			temp.put("disk", typeList.get(i).disk);
			ArrObj.add(temp);
		}
		obj.put("types", ArrObj);
		return obj;
	}
	private static JSONObject vm_destroy(int vmid) throws LibvirtException, IOException
	{
		JSONObject obj = new JSONObject();
		for(int i = 0; i < Ready.size(); i++)
			if(Ready.get(i).vmid == vmid)
			{
				String connect;
				if(hostList.get(Ready.get(i).pmid).hyper.equals("qemu"))
					connect = "qemu+ssh://"+hostList.get(Ready.get(i).pmid).all+"/system?known_hosts=/home/"+hostList.get(Ready.get(i).pmid).name+"/.ssh/known_hosts";
				else
					connect = "xen+ssh://"+hostList.get(Ready.get(i).pmid).all;
				Connect conn = new Connect(connect, false);
				Domain testDomain = conn.domainLookupByName(Ready.get(i).vm_name);
				testDomain.destroy();
				hostList.get(Ready.get(i).pmid).free_cpu += typeList.get(Ready.get(i).instance_type).cpu;
				hostList.get(Ready.get(i).pmid).free_ram += typeList.get(Ready.get(i).instance_type).ram;
				File fl = new File("/var/libvirt/images/host"+i);
				fl.delete();
				FileWriter fw = new FileWriter(fl);
				fw.write(hostList.get(i).free_cpu+"\n");
				fw.write((int) hostList.get(i).free_ram+"\n");
				fw.write(Integer.toString(hostList.get(i).vms - 1));
				fw.close();
				hostList.get(Ready.get(i).pmid).vms --;
				fl = new File("/var/libvirt/images/Ready"+Ready.get(i).vmid);
				fl.delete();
				Ready.remove(i);
				JSONObject exec = Queue_execute();
				if(exec.keys().equals("Queued"))
				{
					obj.put("status", 1);
					return obj;
				}
				else
				{
					obj.put("status", 1);
					return obj;
				}
			}
		obj.put("status", 0);
		return obj;
	}
	private static JSONObject Queue_execute() throws IOException, LibvirtException
	{
		JSONObject obj = new JSONObject();
		int chk = 0;
		if(Queue.size() != 0)
		{	
			do
			{
				chk++;
				Entry Qu = new Entry();
				Qu = Queue.get(0);
				Queue.remove(0);
				obj = vm_create(Qu.name, Qu.type, Qu.image);
			}
			while(obj.keys().equals("Error") && chk!=Queue.size());
		}
		return obj;
	}
	private static JSONObject vm_query(int vmid)
	{
		JSONObject obj = new JSONObject();
		for(int i = 0; i < Ready.size(); i++)
			if(Ready.get(i).vmid == vmid)
			{
				obj.put("vmid", vmid);
				obj.put("name", Ready.get(i).vm_name);
				obj.put("vm_type", Ready.get(i).instance_type);
				obj.put("pmid", Ready.get(i).pmid);
				return obj;
			}
		obj.put("Error", "Invalid VMID");
		return obj;
	}
}