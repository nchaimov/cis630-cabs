package engine;

import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import net.Protocol;
import net.Protocol.OfferHelpResponse;
import net.Protocol.ReceivedAgent;
import world.Agent;
import world.Cell;
import world.LocalCell;
import world.impl.Rabbit;

public class LocalEngine extends Engine {
	
	LocalCell[][] cells;
	ArrayList<RemoteEngine> peerList;
	int globalWidth;
	int globalHeight;
	
	public LocalEngine(int tlx, int tly, int width, int height,
			int globalWidth, int globalHeight) {
		super(tlx, tly, width, height);
		this.globalWidth = globalWidth;
		this.globalHeight = globalHeight;
		peerList = new ArrayList<RemoteEngine>();
		cells = new LocalCell[height][width];
		for (int i = 0; i < this.height; i++) {
			for (int j = 0; j < this.width; j++) {
				cells[i][j] = new LocalCell(tlx + j, tly + i, this);
			}
		}
	}
	
	public void go(int turn) {
		for (LocalCell[] cell : cells) {
			for (LocalCell element : cell) {
				element.go(turn);
			}
		}
	}
	
	public void moveAgent(Agent agent, LocalCell oldCell, int x, int y) {
		Cell newCell = findCell(oldCell.getX() + x, oldCell.getY() + y);
		newCell.add(agent);
		oldCell.remove(agent);
	}
	
	private Cell findRemoteCell(int x, int y) {
		for (int i = 0; i < peerList.size(); i++) {
			if (peerList.get(i).hasCell(x, y)) {
				return peerList.get(i).findCell(x, y);
			}
		}
		System.err.println("Didn't find remote cell: " + x + ", " + y);
		return null;
	}
	
	@Override
	public Cell findCell(int x, int y) {
		if (y >= globalHeight) {
			y = y % globalHeight;
		}
		if (x >= globalWidth) {
			x = x % globalWidth;
		}
		if (y < 0) {
			y = (y % globalHeight) + globalHeight;
		}
		if (x < 0) {
			x = (x % globalWidth) + globalWidth;
		}
		if (hasCell(x, y)) {
			return getCell(x, y);
		} else {
			return findRemoteCell(x, y);
		}
	}
	
	public LocalCell getCell(int x, int y) {
		return cells[y - tly][x - tlx];
	}
	
	public void placeAgent(int x, int y, Agent agent) {
		LocalCell cell = getCell(x, y);
		cell.add(agent);
	}
	
	public void placeAgents(int agents) {
		for (int i = 0; i < agents; i++) {
			LocalCell cell = getCell(0, i);
			cell.add(new Rabbit());
		}
	}
	
	public void print() {
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				LocalCell cell = cells[i][j];
				if (cell.getAgents().size() > 0) {
					System.out.print("* ");
				} else {
					System.out.print("- ");
				}
			}
			System.out.println();
		}
	}
	
	private void handleMessages() {
		for (int i = 0; i < peerList.size(); i++) {
			int messageType = 0;
			try {
				ObjectInputStream in = peerList.get(i).in;
				while (messageType != -1) {
					messageType = in.read();
					switch (messageType) {
					case Protocol.SENDAGENT:
						ReceivedAgent newAgent = Protocol.sendAgent(in);
						this.placeAgent(newAgent.getX(), newAgent.getY(),
								newAgent.getAgent());
						newAgent.getAgent().end();
						break;
					case Protocol.ENDTURN:
						int turn = Protocol.endTurn(in);
						messageType = -1;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private void sendCells(RemoteEngine remote) {
		// TODO: Send agents along with cells.
		int rWidth = this.width / 2;
		int rHeight = this.height;
		int rTlx = this.width - rWidth;
		int rTly = 0;
		Protocol.offerHelpResp(remote.out, rTlx, rTly, rWidth, rHeight,
				globalWidth, globalHeight);
		for (int i = rTlx; i < rWidth; i++) {
			for (int j = rTly; j < rHeight; j++) {
				LocalCell cell = getCell(i, j);
				for (Agent a : cell.getAgents()) {
					Protocol.sendAgent(remote.out, cell.getX(), cell.getY(), a);
				}
			}
		}
		remote.setCoordinates(rTlx, rTly, rWidth, rHeight);
		this.peerList.add(remote);
		// TODO: Actually change the size of the data structure that
		// holds the cells.
		this.width = this.width - rWidth;
		
	}
	
	public static void main(String[] args) {
		
		int globalWidth = 10;
		int globalHeight = 10;
		int port = 1234;
		LocalEngine engine = null;
		boolean isClient = false;
		try {
			
			// Client case
			if (args.length == 1) {
				isClient = true;
				// Use multicast instead.
				InetAddress other = InetAddress.getByName(args[0]);
				Socket socket = new Socket(other, port);
				RemoteEngine server = new RemoteEngine(socket);
				Protocol.offerHelpReq(server.out);
				OfferHelpResponse r = Protocol.offerHelpResp(server.in);
				engine = new LocalEngine(r.getTlx(), r.getTly(), r.getWidth(),
						r.getHeight(), r.getGlobalWidth(), r.getGlobalHeight());
				server.setEngine(engine);
				engine.peerList.add(server);
				server.setCoordinates(0, 0, 5, 10);
				// TODO: Get agents from server.
			}

			// Server case
			else {
				// TODO: Don't hard code everything.
				engine = new LocalEngine(0, 0, globalWidth, globalHeight,
						globalWidth, globalHeight);
				ServerSocket serverSocket = new ServerSocket(port);
				Socket clientSocket = serverSocket.accept();
				RemoteEngine client = new RemoteEngine(clientSocket, engine);
				// This is to read the offerHelpReq message. This
				// should be in a method.
				if (client.in.read() != Protocol.OFFERHELP) {
					throw new Exception("Expected offer help request.");
				}
				// TODO: Use a smart algorithm to figure out what
				// coordinates to assign the other node.
				engine.sendCells(client);
				
				// We probably need some kind of ACK here.
				
				engine.placeAgents(5);
				
			}
			engine.print();
			for (int i = 0; i < 20; i++) {
				Thread.sleep(1000);
				System.out.println("Starting turn " + i);
				engine.go(i);
				for (int j = 0; j < engine.peerList.size(); j++) {
					Protocol.endTurn(engine.peerList.get(j).out, i);
				}
				engine.handleMessages();
				engine.print();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}