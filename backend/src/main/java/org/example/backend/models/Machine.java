package org.example.backend.models;

import org.example.backend.models.ItemQueue;
import org.example.backend.DTOs.ItemDTO;
import org.example.backend.models.Item;
import org.example.backend.DTOs.updateNodeDTO;
import org.example.backend.observers.Observer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Machine implements Runnable {

    static final long TIME_INTERVALS = 100;

    private Observer observer;
    private List<ItemQueue> inputQueues;
    private ItemQueue outputQueue;
    private Item activeItem;
    private long sleepTime;
    private long timeSlept;
    private long id;
    private final Object pauseLock = new Object();
    private volatile boolean isPaused;
    
    public long getId() {
        return id;
    }

    public Machine(List<ItemQueue> inputQueues, ItemQueue outputQueue, long id, Observer observer) {
        this.inputQueues = inputQueues;
        this.outputQueue = outputQueue;
        this.activeItem = null;
        this.sleepTime = getRandomTime();
        this.id = id;
        this.observer = observer;
        this.timeSlept = this.sleepTime;
        this.isPaused = false;
    }

    public void run(){
        int i = 0;
        isPaused = false;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                synchronized (pauseLock) {
                    while (isPaused) {
                        pauseLock.wait();
                    }
                }
                while (timeSlept < sleepTime && !isPaused){
                    long s = Math.min(20, sleepTime - timeSlept);
                    Thread.sleep(s);
                    timeSlept += s;
                }
                if (activeItem != null && !isPaused) {
                    observer.sendMessageToTopic(
                        new updateNodeDTO(
                            id,
                            outputQueue.getId(),
                            "move",
                            activeItem.getDTO()
                        )
                    );
                    outputQueue.push(activeItem);
                    activeItem = null;
                }
                else if (timeSlept >= sleepTime && !isPaused){
                    activeItem = inputQueues.get(i).pop();
                    if (activeItem != null){
                        observer.sendMessageToTopic(
                            new updateNodeDTO(
                                inputQueues.get(i).getId(),
                                id,
                                "move",
                                activeItem.getDTO()
                            )
                        );
                        timeSlept = 0;
                    }
                    i = (i + 1) % inputQueues.size();
                }
            } catch (InterruptedException e) {
                // e.printStackTrace();
                Thread.currentThread().interrupt();
                break;
            }
        }
        // System.out.println("Machine "+ String.valueOf(id)+ " stoped");
    }

    public String getColor() {
        if (activeItem == null)
            return null;
        return activeItem.getColor();
    }

    public List<ItemDTO> getItemDTO() {
        if (activeItem == null)
            return null;
        ItemDTO itemDTO = new ItemDTO(activeItem);
        List<ItemDTO> items = new ArrayList<>();
        items.add(itemDTO);
        return items;
    }

    public List<Long> getInputQueueIds() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < inputQueues.size(); i++){
            ids.add(inputQueues.get(i).getId());
        }
        return ids;
    }

    public long getOutputQueueId() {
        return outputQueue.getId();
    }

    static long getRandomTime() {
        Random random = new Random();
        long randomNumber;
        randomNumber = 1500 + random.nextInt(2000);
        return randomNumber;
    }

    public void pause() {
        synchronized (pauseLock) {
            isPaused = true;
        }
    }

    public void resume() {
        synchronized (pauseLock) {
            isPaused = false;
            pauseLock.notifyAll();
        }
    }

    // remove item in it and calculate new sleep time
    public void clearMachine() {
        this.activeItem = null;
        this.sleepTime = getRandomTime();
    }

    public long getSleepTime() {
        return sleepTime;
    }

    public void setSleepTime(long time) {
        this.sleepTime = time;
    }
}
