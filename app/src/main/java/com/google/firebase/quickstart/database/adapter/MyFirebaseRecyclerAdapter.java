package com.google.firebase.quickstart.database.adapter;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.quickstart.database.activity.PostDetailActivity;
import com.google.firebase.quickstart.database.models.Post;
import com.google.firebase.quickstart.database.viewholder.PostViewHolder;


public class MyFirebaseRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    MyFirebaseArray mSnapshots;
    Context context;
    DatabaseReference mDatabase;

    public MyFirebaseRecyclerAdapter(Context context , Query ref , DatabaseReference mDatabase) {
        this.context = context;
        mSnapshots = new MyFirebaseArray(ref);
        this.mDatabase = mDatabase;

        mSnapshots.setOnChangedListener(new MyFirebaseArray.OnChangedListener() {
            @Override
            public void onChanged(EventType type, int index, int oldIndex) {
                switch (type) {
                    case ADDED:
                        notifyItemInserted(index);
                        break;
                    case CHANGED:
                        notifyItemChanged(index);
                        break;
                    case REMOVED:
                        notifyItemRemoved(index);
                        break;
                    case MOVED:
                        notifyItemMoved(oldIndex, index);
                        break;
                    default:
                        throw new IllegalStateException("Incomplete case statement");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                MyFirebaseRecyclerAdapter.this.onCancelled(databaseError);
            }
        });
    }

    public void cleanup() {
        mSnapshots.cleanup();
    }

    @Override
    public int getItemCount() {
        return mSnapshots.getCount();
    }

    public Post getItem(int position) {
        return parseSnapshot(mSnapshots.getItem(position));
    }

    protected Post parseSnapshot(DataSnapshot snapshot) {
        return snapshot.getValue(Post.class);
    }

    public DatabaseReference getRef(int position) { return mSnapshots.getItem(position).getRef(); }

    @Override
    public long getItemId(int position) {
        // http://stackoverflow.com/questions/5100071/whats-the-purpose-of-item-ids-in-android-listview-adapter
        return mSnapshots.getItem(position).getKey().hashCode();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        final Post model = getItem(position);
		PostViewHolder postViewHolder = (PostViewHolder) viewHolder;
        final DatabaseReference postRef = getRef(position);

        // Set click listener for the whole post view
        final String postKey = postRef.getKey();
        postViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Launch PostDetailActivity
                Intent intent = new Intent(context, PostDetailActivity.class);
                intent.putExtra(PostDetailActivity.EXTRA_POST_KEY, postKey);
                context.startActivity(intent);
            }
        });

        // Determine if the current user has liked this post and set UI accordingly
        if (model.stars.containsKey(getUid())) {
            postViewHolder.starView.setImageResource(R.drawable.ic_toggle_star_24);
        } else {
            postViewHolder.starView.setImageResource(R.drawable.ic_toggle_star_outline_24);
        }

        // Bind Post to ViewHolder, setting OnClickListener for the star button
        postViewHolder.bindToPost(model, new View.OnClickListener() {
            @Override
            public void onClick(View starView) {
                // Need to write to both places the post is stored
                DatabaseReference globalPostRef = mDatabase.child("posts").child(postRef.getKey());
                DatabaseReference userPostRef = mDatabase.child("user-posts").child(model.uid).child(postRef.getKey());

                // Run two transactions
                onStarClicked(globalPostRef);
                onStarClicked(userPostRef);
            }
        });
    }

    protected void onCancelled(DatabaseError databaseError) {
        Log.w("FirebaseRecyclerAdapter", databaseError.toException());
    }

    // [START post_stars_transaction]
    private void onStarClicked(DatabaseReference postRef) {
        postRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData mutableData) {
                Post p = mutableData.getValue(Post.class);
                if (p == null) {
                    return Transaction.success(mutableData);
                }

                if (p.stars.containsKey(getUid())) {
                    // Unstar the post and remove self from stars
                    p.starCount = p.starCount - 1;
                    p.stars.remove(getUid());
                } else {
                    // Star the post and add self to stars
                    p.starCount = p.starCount + 1;
                    p.stars.put(getUid(), true);
                }

                // Set value and report transaction success
                mutableData.setValue(p);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean b, DataSnapshot dataSnapshot) {

            }
        });
    }
    // [END post_stars_transaction]

    public String getUid() {
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }
}
